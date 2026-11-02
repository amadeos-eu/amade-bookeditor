package eu.amadeos.apps.bookeditor.domain.epub;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.KeptKind;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

// --8<-- [start:intro]
/**
 * Takes over an EPUB 2 or 3 that another program made into a new book of the editor. What the editor has a
 * style for comes in as such: chapters (one per file, or one per top heading), headings, paragraphs, quotes,
 * scene breaks, pictures with their descriptions, footnotes and endnotes, links, bold, italic, superscript,
 * subscript and centred text, also where the book sets them through its style sheet. Tables, lists, formulas
 * and drawings are kept unchanged as {@link Block.Kept}. The book's own design is not taken over: the new book
 * gets the look Classic. Cover page, title page and table of contents are left out, because the editor makes
 * its own.
 */
// --8<-- [end:intro]
public final class ForeignEpubReader {
    private static final Pattern SCENE_BREAK = Pattern.compile("^[\\s*•·~#⁂§❦❧✻✽✶◆◇○●–—-]{1,20}$");
    private static final Pattern NOTE_MARK = Pattern.compile("^\\[?\\(?(\\d{1,4}|[*†‡§]+|[a-z])\\)?\\]?$");
    private static final Pattern NOTE_NUMBER = Pattern.compile("^\\s*(\\[?\\d{1,4}\\]?[.):]?|[*†‡§]+)\\s*");
    private static final Pattern SPLIT_FILE = Pattern.compile("(?i).*_split_\\d+\\.x?html?$");
    private static final Pattern CSS_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");
    private static final Pattern CSS_CLASS = Pattern.compile("^[a-z0-9]*\\.([\\w-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SKIPPED = Set.of("script", "style", "head", "nav", "form", "input", "button",
            "iframe", "object", "embed", "video", "audio", "canvas", "noscript", "template", "link", "meta");
    private static final Set<String> CONTAINERS = Set.of("div", "section", "article", "main", "header", "footer",
            "aside", "body", "hgroup", "center", "span", "address", "details", "summary");
    private static final Set<String> BLOCKS = Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "section",
            "article", "main", "header", "footer", "aside", "blockquote", "pre", "figure", "table", "ul", "ol", "dl",
            "hr", "hgroup", "center", "address", "nav", "math", "svg", "details", "summary", "figcaption", "form",
            "iframe", "object", "video", "audio", "canvas", "img", "image");

    /**
     * The book taken over, and how much of it the editor keeps as boxes or had to leave out.
     *
     * @param kept    pieces kept as grey boxes
     * @param dropped pieces left out: videos, sounds, scripts, forms, and pictures in formats EPUB does not allow
     */
    public record Result(Book book, int kept, int dropped) {
    }

    /** What CSS makes of a class: the marks and centring the editor knows. */
    private record ClassStyle(Set<Mark> marks, boolean centered) {
    }

    private record Item(String id, String path, String mediaType, String properties) {
    }

    private final ZipFile zip;
    private final Book book;
    private final Map<String, ClassStyle> classes = new HashMap<>();
    /** The note texts by the id ({@code path#id}) of the note they belong to. */
    private final Map<String, String> notes = new HashMap<>();
    /** Elements that are the text of a note, so they are not also read as paragraphs. */
    private final Set<Element> noteElements = new HashSet<>();
    /** The id of the chapter each content file starts, for links between files. */
    private final Map<String, String> chapterOfFile = new HashMap<>();
    /** The pictures already in the book, by their path in the EPUB. */
    private final Map<String, String> pictureOfPath = new HashMap<>();
    private final Map<String, Item> itemOfPath = new HashMap<>();
    private int topLevel = 1;
    private int kept;
    private int dropped;

    private ForeignEpubReader(ZipFile zip, Book book) {
        this.zip = zip;
        this.book = book;
    }

    public static Result read(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            String opfPath = EpubReader.rootFile(zip);
            Document opf = EpubReader.xml(EpubReader.text(zip, opfPath));
            Element metadata = opf.selectFirst("metadata");
            if (metadata == null) throw new EpubReader.NotEditableException("no metadata in " + opfPath, false);
            BookInfo info = EpubReader.info(metadata);
            if (info.title().isEmpty()) {
                String name = file.getFileName().toString();
                info = info.withTitle(name.replaceFirst("(?i)\\.epub$", ""));
            }
            ForeignEpubReader r = new ForeignEpubReader(zip, new Book(info, Look.CLASSIC));
            r.readAll(opf, opfPath);
            return new Result(r.book, r.kept, r.dropped);
        } catch (ZipException e) {
            throw new EpubReader.NotEditableException("not a zip file: " + e.getMessage(), false);
        }
    }

    // ---------------------------------------------------------------- the package

    private void readAll(Document opf, String opfPath) throws IOException {
        Map<String, Item> manifest = new LinkedHashMap<>();
        for (Element e : opf.getElementsByTag("item")) {
            String path = resolve(opfPath, e.attr("href"));
            Item item = new Item(e.attr("id"), path, e.attr("media-type"), e.attr("properties"));
            manifest.put(item.id(), item);
            itemOfPath.put(path, item);
        }
        for (Item item : manifest.values()) {
            if (item.mediaType().equals("text/css")) styleSheet(item.path());
        }

        // Files the editor makes itself: cover page, title page, table of contents
        Set<String> skip = new HashSet<>();
        for (Item item : manifest.values()) if (item.properties().contains("nav")) skip.add(item.path());
        for (Element ref : opf.select("guide > reference")) {
            String type = ref.attr("type").toLowerCase(Locale.ROOT);
            if (type.equals("cover") || type.equals("title-page") || type.equals("toc")) {
                skip.add(resolve(opfPath, ref.attr("href")));
            }
        }

        // The cover picture
        String coverPath = "";
        for (Item item : manifest.values()) if (item.properties().contains("cover-image")) coverPath = item.path();
        if (coverPath.isEmpty()) {
            for (Element m : opf.getElementsByTag("meta")) {
                if (m.attr("name").equals("cover") && manifest.containsKey(m.attr("content"))) {
                    coverPath = manifest.get(m.attr("content")).path();
                }
            }
        }
        if (!coverPath.isEmpty()) {
            String name = picture(coverPath);
            if (name != null) book.setCover(name);
        }

        // The content files in reading order
        List<String> files = new ArrayList<>();
        Map<String, Document> docs = new HashMap<>();
        for (Element ref : opf.getElementsByTag("itemref")) {
            Item item = manifest.get(ref.attr("idref"));
            if (item == null || !item.mediaType().contains("html") || skip.contains(item.path())) continue;
            Document doc = EpubReader.xml(EpubReader.text(zip, item.path()));
            doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml).charset(StandardCharsets.UTF_8);
            if (frontPage(doc, files.isEmpty())) continue;
            files.add(item.path());
            docs.put(item.path(), doc);
            chapterOfFile.put(item.path(), Chapter.newId());
        }

        topLevel = chapterLevel(files.stream().map(docs::get).toList());

        for (String f : files) collectNotes(f, docs);

        List<Chapter> chapters = new ArrayList<>();
        for (String f : files) {
            Document doc = docs.get(f);
            Element body = body(doc);
            boolean hadNotes = body.select("*").stream().anyMatch(noteElements::contains);
            List<Block> blocks = new ArrayList<>();
            new Converter(f, blocks).container(body);
            if (dedication(body) && book.dedication().isEmpty()) {
                if (!blocks.isEmpty()) book.setDedication(new Chapter(Book.DEDICATION_ID, false, withoutTitle(blocks)));
                continue;
            }
            List<List<Block>> parts = split(blocks);
            boolean continuation = SPLIT_FILE.matcher(f).matches();
            for (int i = 0; i < parts.size(); i++) {
                List<Block> part = parts.get(i);
                boolean titled = !part.isEmpty() && part.getFirst() instanceof Block.Paragraph p
                        && p.style() == ParaStyle.CHAPTER_TITLE;
                if (!hasContent(part, titled) && (hadNotes || !titled)) continue;
                if (i == 0 && !titled && continuation && !chapters.isEmpty()) {
                    Chapter last = chapters.removeLast();
                    List<Block> joined = new ArrayList<>(last.blocks());
                    joined.addAll(part);
                    chapters.add(last.withBlocks(joined));
                    chapterOfFile.put(f, last.id());
                    continue;
                }
                String id = i == 0 ? chapterOfFile.get(f) : Chapter.newId();
                if (!titled) part.addFirst(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, ""));
                chapters.add(new Chapter(id, false, part));
            }
        }
        // Links to files that came to nothing point to the next chapter that exists
        List<Chapter> fixed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Chapter c : chapters) ids.add(c.id());
        for (Chapter c : chapters) fixed.add(c.withBlocks(fixLinks(c.blocks(), ids, files)));
        if (fixed.isEmpty()) fixed.add(Chapter.create(""));
        book.setChapters(fixed);
    }

    // --8<-- [start:level]
    /**
     * The heading level that starts chapters: the highest one used more than once in the book. A level used only
     * once is the book's own title (as in many older e-books with one {@code h1} and the chapters in {@code h2}).
     */
    static int chapterLevel(List<Document> docs) {
        int[] count = new int[7];
        for (Document d : docs) {
            for (int level = 1; level <= 6; level++) count[level] += body(d).select("h" + level).size();
        }
        for (int level = 1; level <= 6; level++) if (count[level] >= 2) return level;
        for (int level = 1; level <= 6; level++) if (count[level] >= 1) return level;
        return 1;
    }
    // --8<-- [end:level]

    private static Element body(Document doc) {
        Element body = doc.selectFirst("body");
        return body == null ? doc : body;
    }

    private static String epubType(Element e) {
        return (e.attr("epub:type") + " " + e.attr("role")).toLowerCase(Locale.ROOT);
    }

    /** A cover page or title page: left out, the editor makes its own. */
    private boolean frontPage(Document doc, boolean first) {
        Element body = body(doc);
        for (Element e : body.select("*")) {
            String t = epubType(e);
            if (t.contains("cover") || t.contains("titlepage") || t.contains("doc-toc") || t.matches(".*\\btoc\\b.*")) {
                return true;
            }
        }
        boolean onlyPicture = body.text().isBlank() && !body.select("img, image").isEmpty();
        return onlyPicture && first;
    }

    private static boolean dedication(Element body) {
        for (Element e : body.select("*")) if (epubType(e).contains("dedication")) return true;
        return false;
    }

    private static List<Block> withoutTitle(List<Block> blocks) {
        List<Block> out = new ArrayList<>(blocks);
        if (!out.isEmpty() && out.getFirst() instanceof Block.Paragraph p && p.style() == ParaStyle.CHAPTER_TITLE) {
            out.removeFirst();
        }
        return out;
    }

    private static boolean hasContent(List<Block> blocks, boolean titled) {
        for (int i = titled ? 1 : 0; i < blocks.size(); i++) {
            if (!(blocks.get(i) instanceof Block.Paragraph p) || !p.plainText().isBlank()
                    || p.content().stream().anyMatch(x -> x instanceof Inline.Footnote)) {
                return true;
            }
        }
        return false;
    }

    /** One part per chapter title; a file with several top headings holds several chapters. */
    private static List<List<Block>> split(List<Block> blocks) {
        List<List<Block>> parts = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        for (Block b : blocks) {
            boolean title = b instanceof Block.Paragraph p && p.style() == ParaStyle.CHAPTER_TITLE;
            if (title && !current.isEmpty() && (current.size() > 1 || !(current.getFirst() instanceof Block.Paragraph first
                    && first.style() == ParaStyle.CHAPTER_TITLE))) {
                parts.add(current);
                current = new ArrayList<>();
            } else if (title && current.size() == 1) {
                // Two titles in a row ("Chapter 3" / "The Storm"): one title
                Block.Paragraph first = (Block.Paragraph) current.getFirst();
                List<Inline> joined = new ArrayList<>(first.content());
                joined.add(new Inline.Text(": "));
                joined.addAll(((Block.Paragraph) b).content());
                current.set(0, new Block.Paragraph(ParaStyle.CHAPTER_TITLE, joined));
                continue;
            }
            current.add(b);
        }
        if (!current.isEmpty()) parts.add(current);
        return parts;
    }

    private List<Block> fixLinks(List<Block> blocks, Set<String> ids, List<String> files) {
        List<Block> out = new ArrayList<>();
        for (Block b : blocks) {
            if (!(b instanceof Block.Paragraph p)) {
                out.add(b);
                continue;
            }
            List<Inline> content = new ArrayList<>();
            for (Inline i : p.content()) {
                if (i instanceof Inline.Text t && Link.chapterId(t.link()).filter(id -> !ids.contains(id)).isPresent()) {
                    String target = targetFor(Link.chapterId(t.link()).get(), ids, files);
                    content.add(new Inline.Text(t.text(), t.marks(), target.isEmpty() ? "" : Link.chapter(target)));
                } else {
                    content.add(i);
                }
            }
            out.add(new Block.Paragraph(p.style(), content));
        }
        return out;
    }

    private String targetFor(String missing, Set<String> ids, List<String> files) {
        boolean after = false;
        for (String f : files) {
            String id = chapterOfFile.get(f);
            if (id.equals(missing)) after = true;
            else if (after && ids.contains(id)) return id;
        }
        return "";
    }

    // ---------------------------------------------------------------- style sheets

    private void styleSheet(String path) {
        String css;
        try {
            css = EpubReader.text(zip, path).replaceAll("(?s)/\\*.*?\\*/", "");
        } catch (IOException e) {
            return;
        }
        Matcher m = CSS_RULE.matcher(css);
        while (m.find()) {
            String body = m.group(2).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            Set<Mark> marks = EnumSet.noneOf(Mark.class);
            if (body.matches(".*font-weight:(bold|bolder|[6-9]00).*")) marks.add(Mark.BOLD);
            if (body.matches(".*font-style:(italic|oblique).*")) marks.add(Mark.ITALIC);
            if (body.contains("vertical-align:super")) marks.add(Mark.SUPERSCRIPT);
            if (body.contains("vertical-align:sub")) marks.add(Mark.SUBSCRIPT);
            boolean centered = body.contains("text-align:center");
            if (marks.isEmpty() && !centered) continue;
            for (String selector : m.group(1).split(",")) {
                Matcher c = CSS_CLASS.matcher(selector.strip());
                if (!c.matches()) continue;
                ClassStyle old = classes.get(c.group(1));
                Set<Mark> all = EnumSet.copyOf(marks.isEmpty() ? EnumSet.noneOf(Mark.class) : marks);
                if (old != null) all.addAll(old.marks());
                classes.put(c.group(1), new ClassStyle(all, centered || (old != null && old.centered())));
            }
        }
    }

    private Set<Mark> marksOf(Element e) {
        Set<Mark> marks = EnumSet.noneOf(Mark.class);
        for (String c : e.classNames()) {
            ClassStyle s = classes.get(c);
            if (s != null) marks.addAll(s.marks());
        }
        String style = e.attr("style").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (style.matches(".*font-weight:(bold|bolder|[6-9]00).*")) marks.add(Mark.BOLD);
        if (style.matches(".*font-style:(italic|oblique).*")) marks.add(Mark.ITALIC);
        return marks;
    }

    private boolean centered(Element e) {
        for (String c : e.classNames()) {
            ClassStyle s = classes.get(c);
            if (s != null && s.centered()) return true;
        }
        return e.attr("style").toLowerCase(Locale.ROOT).replaceAll("\\s+", "").contains("text-align:center")
                || e.attr("align").equalsIgnoreCase("center");
    }

    // ---------------------------------------------------------------- notes

    // --8<-- [start:notes]
    /**
     * Finds the note references of a file: links marked as such ({@code epub:type="noteref"}) and, in older
     * books, short links such as "1" or "[2]" in superscript. The text the link points to becomes the footnote,
     * without its number and its link back.
     */
    private void collectNotes(String file, Map<String, Document> docs) {
        for (Element a : body(docs.get(file)).select("a[href]")) {
            if (!noteRef(a)) continue;
            String target = target(file, a.attr("href"));
            int hash = target.indexOf('#');
            if (hash < 0) continue;
            Document doc = docs.get(target.substring(0, hash));
            if (doc == null) continue;
            Element note = doc.getElementById(target.substring(hash + 1));
            if (note == null) continue;
            Element holder = note;
            // An anchor inside the note's paragraph (<p><a id="n1" href="…">1</a> Text</p>)
            if ((note.tagName().equals("a") || note.tagName().equals("span") || note.tagName().equals("sup"))
                    && note.parent() != null) {
                holder = note.parent();
                if (holder.tagName().equals("sup") && holder.parent() != null) holder = holder.parent();
            }
            if (holder.tagName().equals("body")) continue;
            notes.put(target, noteText(holder));
            noteElements.add(holder);
        }
    }
    // --8<-- [end:notes]

    private boolean noteRef(Element a) {
        String type = epubType(a);
        if (type.contains("noteref")) return true;
        if (!a.attr("href").contains("#")) return false;
        String text = a.text().strip();
        boolean sup = a.parent() != null && a.parent().tagName().equals("sup") || a.selectFirst("sup") != null
                || a.className().toLowerCase(Locale.ROOT).contains("note");
        return sup && NOTE_MARK.matcher(text).matches();
    }

    private static String noteText(Element holder) {
        Element copy = holder.clone();
        for (Element back : copy.select("a")) {
            String type = epubType(back);
            if (type.contains("backlink") || NOTE_MARK.matcher(back.text().strip().replaceAll("[.)]$", "")).matches()) {
                back.remove();
            }
        }
        String text = NOTE_NUMBER.matcher(copy.text().strip()).replaceFirst("").strip();
        return text.replaceFirst("^[.):\\]]+\\s*", "").strip();
    }

    // ---------------------------------------------------------------- paths and pictures

    /** A path inside the EPUB, from a link in the file at {@code from}. */
    static String resolve(String from, String href) {
        String h = URLDecoder.decode(href.replace("+", "%2B"), StandardCharsets.UTF_8);
        int hash = h.indexOf('#');
        String fragment = hash < 0 ? "" : h.substring(hash);
        h = hash < 0 ? h : h.substring(0, hash);
        if (h.isEmpty()) return from + fragment;
        String base = from.contains("/") ? from.substring(0, from.lastIndexOf('/') + 1) : "";
        List<String> parts = new ArrayList<>();
        for (String p : (base + h).split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
            } else {
                parts.add(p);
            }
        }
        return String.join("/", parts) + fragment;
    }

    private String target(String file, String href) {
        return resolve(file, href);
    }

    private static boolean external(String href) {
        try {
            return URI.create(href.strip()).getScheme() != null;
        } catch (IllegalArgumentException e) {
            return href.matches("(?i)^[a-z]+:.*");
        }
    }

    /** Puts a picture of the EPUB into the book; null if it is missing or of a type EPUB does not allow. */
    private String picture(String path) {
        if (pictureOfPath.containsKey(path)) return pictureOfPath.get(path);
        Item item = itemOfPath.get(path);
        String name = path.substring(path.lastIndexOf('/') + 1);
        String type = item != null && item.mediaType().startsWith("image/") ? item.mediaType() : Resource.mediaTypeOf(name);
        if (!Set.of("image/jpeg", "image/png", "image/gif", "image/svg+xml", "image/webp").contains(type)) return null;
        try {
            String added = book.addResource(name, type, EpubReader.bytes(zip, path));
            pictureOfPath.put(path, added);
            return added;
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- content

    /** Turns the content of one file into blocks. */
    private final class Converter {
        private final String file;
        private final List<Block> out;

        Converter(String file, List<Block> out) {
            this.file = file;
            this.out = out;
        }

        /** An element holding blocks: its block children in turn, loose text in between as paragraphs. */
        void container(Element e) {
            List<Node> inline = new ArrayList<>();
            for (Node child : e.childNodes()) {
                if (child instanceof Element c && block(c)) {
                    paragraphOf(inline, ParaStyle.TEXT, false);
                    inline.clear();
                    element(c);
                } else {
                    inline.add(child);
                }
            }
            paragraphOf(inline, ParaStyle.TEXT, false);
        }

        private boolean block(Element e) {
            if (BLOCKS.contains(e.tagName())) {
                // A span or a div used inside a line stays part of the line
                return !(e.tagName().equals("span"));
            }
            if (e.tagName().equals("span") || e.tagName().equals("a")) {
                for (Element c : e.children()) if (BLOCKS.contains(c.tagName()) && !c.tagName().equals("img")) return true;
            }
            return SKIPPED.contains(e.tagName());
        }

        private void element(Element e) {
            if (noteElements.contains(e)) return;
            String tag = e.tagName();
            String type = epubType(e);
            if (SKIPPED.contains(tag)) {
                if (!tag.equals("nav") && !tag.equals("head") && !tag.equals("style") && !tag.equals("link")
                        && !tag.equals("meta")) {
                    dropped++;
                }
                return;
            }
            if (type.contains("pagebreak")) return;
            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    paragraph(e, headingStyle(tag.charAt(1) - '0'));
                }
                case "hgroup" -> hgroup(e);
                case "p" -> {
                    String text = e.text().strip();
                    if (!text.isEmpty() && SCENE_BREAK.matcher(text).matches() && e.select("img, image").isEmpty()) {
                        out.add(new Block.SceneBreak());
                    } else if (text.isEmpty() && e.select("img, image").isEmpty()
                            && e.className().toLowerCase(Locale.ROOT).matches(".*(break|scene|space).*")) {
                        out.add(new Block.SceneBreak());
                    } else {
                        paragraph(e, centered(e) ? ParaStyle.CENTERED : ParaStyle.TEXT);
                    }
                }
                case "hr" -> out.add(new Block.SceneBreak());
                case "blockquote" -> quote(e, type.contains("epigraph") || e.hasClass("epigraph")
                        ? ParaStyle.EPIGRAPH : ParaStyle.QUOTE);
                case "pre" -> {
                    for (String line : e.wholeText().replace("\r", "").split("\n", -1)) {
                        out.add(new Block.Paragraph(ParaStyle.CODE, line));
                    }
                    while (out.getLast() instanceof Block.Paragraph p && p.style() == ParaStyle.CODE && p.isEmpty()
                            && out.size() > 1) {
                        out.removeLast();
                    }
                }
                case "figure" -> figure(e);
                case "img", "image" -> picture(e, "");
                case "table" -> keep(e, KeptKind.TABLE);
                case "ul", "ol", "dl" -> keep(e, KeptKind.LIST);
                case "math" -> keep(e, KeptKind.FORMULA);
                case "svg" -> {
                    Element image = e.selectFirst("image");
                    if (image != null && e.text().isBlank()) picture(image, "");
                    else keep(e, KeptKind.DRAWING);
                }
                default -> {
                    if (type.contains("epigraph")) {
                        quote(e, ParaStyle.EPIGRAPH);
                    } else if (CONTAINERS.contains(tag) || tag.equals("figcaption")) {
                        boolean hasBlocks = e.children().stream().anyMatch(this::block);
                        if (hasBlocks) container(e);
                        else paragraph(e, centered(e) ? ParaStyle.CENTERED : ParaStyle.TEXT);
                    } else {
                        container(e);
                    }
                }
            }
        }

        /** Above the chapter level: the book's own title inside the text, kept as centred text. */
        private ParaStyle headingStyle(int level) {
            return level < topLevel ? ParaStyle.CENTERED : level == topLevel ? ParaStyle.CHAPTER_TITLE
                    : level == topLevel + 1 ? ParaStyle.HEADING : ParaStyle.SUBHEADING;
        }

        /** A heading with its number or subtitle ("I" and "Down the Rabbit-Hole"): one heading. */
        private void hgroup(Element e) {
            ParaStyle style = null;
            List<Inline> content = new ArrayList<>();
            List<Element> pictures = new ArrayList<>();
            for (Element part : e.children()) {
                String tag = part.tagName();
                if (tag.matches("h[1-6]")) {
                    ParaStyle s = headingStyle(tag.charAt(1) - '0');
                    if (style == null || s.ordinal() < style.ordinal()) style = s;
                }
                List<Inline> piece = new ArrayList<>();
                walk(part, EnumSet.noneOf(Mark.class), "", piece, pictures);
                trim(piece);
                if (piece.isEmpty()) continue;
                if (!content.isEmpty()) content.add(new Inline.Text(": "));
                content.addAll(piece);
            }
            if (style == null || style == ParaStyle.CENTERED) {
                container(e);
                return;
            }
            out.add(new Block.Paragraph(style, content));
            for (Element img : pictures) picture(img, "");
        }

        private void quote(Element e, ParaStyle style) {
            List<Block> inner = new ArrayList<>();
            new Converter(file, inner).container(e);
            for (Block b : inner) {
                if (b instanceof Block.Paragraph p && (p.style() == ParaStyle.TEXT || p.style() == ParaStyle.CENTERED)) {
                    out.add(new Block.Paragraph(style, p.content()));
                } else {
                    out.add(b);
                }
            }
        }

        private void figure(Element e) {
            Element caption = e.selectFirst("figcaption");
            String captionText = caption == null ? "" : caption.text();
            List<Element> pictures = e.select("img, image");
            if (pictures.isEmpty()) {
                if (e.selectFirst("table, math, svg") != null) container(e);
                else paragraph(e, ParaStyle.TEXT);
                return;
            }
            for (int i = 0; i < pictures.size(); i++) picture(pictures.get(i), i == pictures.size() - 1 ? captionText : "");
        }

        private void picture(Element img, String caption) {
            String src = img.hasAttr("src") ? img.attr("src") : img.hasAttr("xlink:href") ? img.attr("xlink:href")
                    : img.attr("href");
            if (src.isEmpty() || external(src)) {
                dropped++;
                return;
            }
            String name = ForeignEpubReader.this.picture(resolve(file, src).replaceFirst("#.*$", ""));
            if (name == null) {
                dropped++;
                return;
            }
            String alt = img.attr("alt");
            if (alt.matches("(?i).*\\.(jpe?g|png|gif|svg|webp)$")) alt = "";
            out.add(new Block.Picture(name, alt, caption, PictureSize.MEDIUM));
        }

        // --8<-- [start:keep]
        /** Keeps a table, list, formula or drawing as it is, with its pictures moved into the book. */
        private void keep(Element e, KeptKind kind) {
            if (contents(e)) return;
            Element copy = e.clone();
            for (Element x : copy.select("script, style, iframe, object, embed, video, audio, form")) x.remove();
            for (Element x : copy.select("*")) {
                x.removeAttr("id");
                x.removeAttr("class");
                for (var attr : new ArrayList<>(x.attributes().asList())) {
                    if (attr.getKey().toLowerCase(Locale.ROOT).startsWith("on")) x.removeAttr(attr.getKey());
                }
            }
            for (Element img : copy.select("img[src], image")) {
                String key = img.tagName().equals("img") ? "src" : img.hasAttr("xlink:href") ? "xlink:href" : "href";
                String src = img.attr(key);
                String name = src.isEmpty() || external(src) ? null
                        : ForeignEpubReader.this.picture(resolve(file, src).replaceFirst("#.*$", ""));
                if (name == null) {
                    img.remove();
                    dropped++;
                } else {
                    img.attr(key, "../images/" + name);
                }
            }
            for (Element a : copy.select("a")) {
                String href = a.attr("href");
                if (!href.isEmpty() && !Link.web(href)) a.unwrap();
            }
            if (copy.text().isBlank() && copy.select("img, image, math, svg").isEmpty()) return;
            out.add(new Block.Kept(kind, copy.outerHtml(), KeptText.words(copy)));
            kept++;
        }
        // --8<-- [end:keep]

        /** A table of contents inside the text: mostly links into the book. The editor makes its own. */
        private boolean contents(Element e) {
            int links = 0, linked = 0;
            for (Element a : e.select("a[href]")) {
                if (Link.web(a.attr("href"))) continue;
                links++;
                linked += a.text().length();
            }
            return links >= 3 && linked * 2 >= e.text().length();
        }

        private void paragraph(Element e, ParaStyle style) {
            List<Node> nodes = new ArrayList<>(e.childNodes());
            paragraphOf(nodes, style, true);
        }

        /** A paragraph from inline content; pictures in it follow the paragraph. */
        private void paragraphOf(List<Node> nodes, ParaStyle style, boolean keepEmpty) {
            List<Inline> content = new ArrayList<>();
            List<Element> pictures = new ArrayList<>();
            for (Node n : nodes) walk(n, EnumSet.noneOf(Mark.class), "", content, pictures);
            trim(content);
            boolean empty = content.stream().allMatch(i -> i instanceof Inline.Text t && t.text().isBlank());
            if (!empty) out.add(new Block.Paragraph(style, content));
            else if (keepEmpty && style.heading() && pictures.isEmpty()) out.add(new Block.Paragraph(style, List.of()));
            for (Element img : pictures) picture(img, "");
        }

        private void walk(Node node, Set<Mark> marks, String link, List<Inline> out, List<Element> pictures) {
            if (node instanceof TextNode t) {
                String text = t.getWholeText().replaceAll("[\\s\\u00AD]+", " ").replace("­", "");
                if (!text.isEmpty()) out.add(new Inline.Text(text, marks, link));
                return;
            }
            if (!(node instanceof Element e)) return;
            if (noteElements.contains(e)) return;
            String tag = e.tagName();
            if (SKIPPED.contains(tag)) return;
            if (tag.equals("img") || tag.equals("image") || tag.equals("svg") && e.selectFirst("image") != null) {
                pictures.add(tag.equals("svg") ? e.selectFirst("image") : e);
                return;
            }
            if (tag.equals("br")) {
                out.add(new Inline.Text(" ", marks, link));
                return;
            }
            if (epubType(e).contains("pagebreak")) return;
            if (tag.equals("a") && e.hasAttr("href") && noteRef(e)) {
                String text = notes.get(target(file, e.attr("href")));
                if (text != null) {
                    out.add(new Inline.Footnote(text));
                    return;
                }
            }
            if (tag.equals("sup") && e.children().size() == 1 && e.child(0).tagName().equals("a")
                    && e.text().strip().equals(e.child(0).text().strip()) && noteRef(e.child(0))) {
                String text = notes.get(target(file, e.child(0).attr("href")));
                if (text != null) {
                    out.add(new Inline.Footnote(text));
                    return;
                }
            }
            Set<Mark> inner = EnumSet.noneOf(Mark.class);
            inner.addAll(marks);
            inner.addAll(marksOf(e));
            String innerLink = link;
            switch (tag) {
                case "strong", "b" -> inner.add(Mark.BOLD);
                case "em", "i", "cite", "dfn", "var" -> inner.add(Mark.ITALIC);
                case "sup" -> inner.add(Mark.SUPERSCRIPT);
                case "sub" -> inner.add(Mark.SUBSCRIPT);
                case "a" -> {
                    String href = e.attr("href");
                    if (Link.web(href)) {
                        innerLink = href;
                    } else if (!href.isEmpty() && !external(href)) {
                        String path = target(file, href).replaceFirst("#.*$", "");
                        String id = chapterOfFile.get(path);
                        if (id != null && !path.equals(file)) innerLink = Link.chapter(id);
                    }
                }
                default -> {
                }
            }
            for (Node child : e.childNodes()) walk(child, inner, innerLink, out, pictures);
        }

        private static void trim(List<Inline> content) {
            // One space where two pieces meet with a space each ("DIE " and " VERWANDLUNG")
            for (int i = 1; i < content.size(); i++) {
                if (content.get(i - 1) instanceof Inline.Text a && content.get(i) instanceof Inline.Text b
                        && a.text().endsWith(" ") && b.text().startsWith(" ")) {
                    content.set(i, new Inline.Text(b.text().substring(1), b.marks(), b.link()));
                }
            }
            content.removeIf(i -> i instanceof Inline.Text t && t.text().isEmpty());
            while (!content.isEmpty() && content.getFirst() instanceof Inline.Text t && t.text().isBlank()) content.removeFirst();
            while (!content.isEmpty() && content.getLast() instanceof Inline.Text t && t.text().isBlank()) content.removeLast();
            if (!content.isEmpty() && content.getFirst() instanceof Inline.Text t) {
                content.set(0, new Inline.Text(t.text().stripLeading(), t.marks(), t.link()));
            }
            if (!content.isEmpty() && content.getLast() instanceof Inline.Text t) {
                content.set(content.size() - 1, new Inline.Text(t.text().stripTrailing(), t.marks(), t.link()));
            }
        }
    }
}
