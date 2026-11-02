package eu.amadeos.apps.bookeditor.domain.epub;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.KeptKind;
import eu.amadeos.apps.bookeditor.domain.book.License;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.Person;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import eu.amadeos.apps.bookeditor.domain.book.Role;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Opens a book the editor saved. Books made elsewhere carry their own HTML and style sheets; this reader says so
 * with {@link NotEditableException}, and {@link eu.amadeos.apps.bookeditor.domain.importer.EpubImport} takes
 * them over into a new book.
 */
public final class EpubReader {
    private static final Pattern CHAPTER_FILE = Pattern.compile("(?:.*/)?chapter-([a-z0-9-]+)\\.xhtml");

    /** The book was not made with the editor (or is not an EPUB at all). */
    public static final class NotEditableException extends IOException {
        private final boolean epub;

        public NotEditableException(String message, boolean epub) {
            super(message);
            this.epub = epub;
        }

        /** True for a valid EPUB made by another program, false for a file that is no EPUB. */
        public boolean epub() {
            return epub;
        }
    }

    private EpubReader() {
    }

    public static Book read(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            String opfPath = rootFile(zip);
            Document opf = xml(text(zip, opfPath));
            Element metadata = opf.selectFirst("metadata");
            if (metadata == null) throw new NotEditableException("no metadata in " + opfPath, false);
            boolean ours = metadata.getElementsByTag("meta").stream()
                    .anyMatch(m -> m.attr("property").equals(EpubWriter.EDITOR_PROPERTY));
            if (!ours) throw new NotEditableException("not made with the Book Editor", true);
            String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

            Book book = new Book(info(metadata), Look.of(meta(metadata, EpubWriter.LOOK_PROPERTY)));
            Map<String, Element> manifest = new LinkedHashMap<>();
            for (Element item : opf.getElementsByTag("item")) manifest.put(item.attr("id"), item);
            String coverName = "";
            for (Element item : manifest.values()) {
                String href = item.attr("href");
                if (!href.startsWith("images/")) continue;
                byte[] data = bytes(zip, base + href);
                String name = href.substring("images/".length());
                book.putResource(new Resource(name, item.attr("media-type"), data));
                if (item.attr("properties").contains("cover-image")) coverName = name;
            }
            if (!coverName.isEmpty()) book.setCover(coverName);
            List<Chapter> chapters = new ArrayList<>();
            for (Element ref : opf.getElementsByTag("itemref")) {
                Element item = manifest.get(ref.attr("idref"));
                if (item == null) continue;
                String href = item.attr("href");
                Matcher m = CHAPTER_FILE.matcher(href);
                if (m.matches()) {
                    chapters.add(chapter(m.group(1), text(zip, base + href)));
                } else if (href.endsWith("dedication.xhtml")) {
                    book.setDedication(chapter(Book.DEDICATION_ID, text(zip, base + href)));
                }
            }
            book.setChapters(chapters);
            return book;
        } catch (java.util.zip.ZipException e) {
            throw new NotEditableException("not a zip file: " + e.getMessage(), false);
        }
    }

    static String rootFile(ZipFile zip) throws IOException {
        if (zip.getEntry("META-INF/container.xml") == null) throw new NotEditableException("no container.xml", false);
        Element root = xml(text(zip, "META-INF/container.xml")).selectFirst("rootfile");
        if (root == null || root.attr("full-path").isEmpty()) throw new NotEditableException("no root file", false);
        return root.attr("full-path");
    }

    static Document xml(String text) {
        Document doc = Jsoup.parse(text, "", Parser.xmlParser());
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    static String text(ZipFile zip, String name) throws IOException {
        return new String(bytes(zip, name), StandardCharsets.UTF_8);
    }

    static byte[] bytes(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("missing in the book: " + name);
        try (var in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    // ---------------------------------------------------------------- metadata

    private static String meta(Element metadata, String property) {
        for (Element m : metadata.getElementsByTag("meta")) {
            if (m.attr("property").equals(property) && m.attr("refines").isEmpty()) return m.text().strip();
        }
        return "";
    }

    private static String refined(Element metadata, String id, String property) {
        if (id.isEmpty()) return "";
        for (Element m : metadata.getElementsByTag("meta")) {
            if (m.attr("refines").equals("#" + id) && m.attr("property").equals(property)) return m.text().strip();
        }
        return "";
    }

    static BookInfo info(Element md) {
        String identifier = "", isbn = "";
        for (Element e : md.getElementsByTag("dc:identifier")) {
            String v = e.text().strip();
            if (v.startsWith("urn:isbn:")) isbn = v.substring("urn:isbn:".length());
            else if (identifier.isEmpty()) identifier = v;
        }
        if (!meta(md, EpubWriter.ISBN_PROPERTY).isEmpty()) isbn = meta(md, EpubWriter.ISBN_PROPERTY);
        String title = "", subtitle = "";
        for (Element e : md.getElementsByTag("dc:title")) {
            String type = refined(md, e.id(), "title-type");
            if (type.equals("subtitle")) subtitle = e.text();
            else if (title.isEmpty()) title = e.text();
        }
        List<Person> people = new ArrayList<>();
        for (Element e : md.select("*")) {
            if (!e.tagName().equals("dc:creator") && !e.tagName().equals("dc:contributor")) continue;
            String code = refined(md, e.id(), "role");
            if (code.isEmpty()) code = e.attr("opf:role"); // EPUB 2
            Role role = code.isEmpty() ? (e.tagName().equals("dc:creator") ? Role.AUTHOR : Role.EDITOR) : Role.of(code);
            people.add(new Person(e.text(), role));
        }
        String series = "";
        int volume = 0;
        for (Element m : md.getElementsByTag("meta")) {
            if (m.attr("property").equals("belongs-to-collection")) {
                series = m.text();
                try {
                    volume = (int) Double.parseDouble(refined(md, m.id(), "group-position"));
                } catch (NumberFormatException e) {
                    volume = 0;
                }
            }
        }
        List<String> keywords = md.getElementsByTag("dc:subject").stream().map(Element::text).toList();
        return new BookInfo(identifier.isEmpty() ? "urn:uuid:" + java.util.UUID.randomUUID() : identifier, title, subtitle,
                people, first(md, "dc:language"), series, volume, first(md, "dc:publisher"), first(md, "dc:date"),
                isbn, keywords, wholeText(md, "dc:description"), License.fromRights(first(md, "dc:rights")),
                meta(md, "schema:accessibilitySummary").startsWith("A book of reflowable text") ? ""
                        : meta(md, "schema:accessibilitySummary"));
    }

    private static String first(Element md, String tag) {
        Element e = md.getElementsByTag(tag).first();
        return e == null ? "" : e.text().strip();
    }

    private static String wholeText(Element md, String tag) {
        Element e = md.getElementsByTag(tag).first();
        return e == null ? "" : e.wholeText().strip();
    }

    // ---------------------------------------------------------------- chapters

    // --8<-- [start:chapter]
    /** Reads back what {@link ChapterWriter} wrote. */
    static Chapter chapter(String id, String xhtml) {
        Document doc = xml(xhtml);
        Element body = doc.selectFirst("body");
        if (body == null) return new Chapter(id, false, List.of());
        Element section = body.selectFirst("section");
        Element root = section == null ? body : section;
        Map<String, String> notes = new HashMap<>();
        for (Element aside : body.getElementsByTag("aside")) {
            Element copy = aside.clone();
            Element back = copy.selectFirst("a[role=doc-backlink]");
            if (back != null) back.remove();
            notes.put(aside.id(), copy.text().strip());
        }
        boolean numbered = false;
        List<Block> blocks = new ArrayList<>();
        for (Element e : root.children()) {
            String tag = e.tagName();
            if (e.hasClass("chapter-label")) {
                numbered = true;
                continue;
            }
            switch (tag) {
                case "h1" -> blocks.add(paragraph(ParaStyle.CHAPTER_TITLE, e, notes));
                case "h2" -> blocks.add(paragraph(ParaStyle.HEADING, e, notes));
                case "h3" -> blocks.add(paragraph(ParaStyle.SUBHEADING, e, notes));
                case "p" -> blocks.add(paragraph(e.hasClass("centered") ? ParaStyle.CENTERED : ParaStyle.TEXT, e, notes));
                case "hr" -> blocks.add(new Block.SceneBreak());
                case "blockquote" -> {
                    ParaStyle style = e.hasClass("epigraph") ? ParaStyle.EPIGRAPH : ParaStyle.QUOTE;
                    for (Element p : e.getElementsByTag("p")) blocks.add(paragraph(style, p, notes));
                }
                case "div" -> {
                    if (e.hasClass("kept")) {
                        blocks.add(new Block.Kept(KeptKind.of(e.attr("data-kind")), e.html().strip(), KeptText.words(e)));
                    } else if (e.hasClass("poem")) {
                        boolean firstStanza = true;
                        for (Element stanza : e.children()) {
                            if (!firstStanza) blocks.add(new Block.Paragraph(ParaStyle.POEM, ""));
                            firstStanza = false;
                            for (Element p : stanza.getElementsByTag("p")) blocks.add(paragraph(ParaStyle.POEM, p, notes));
                        }
                    } else {
                        ParaStyle style = e.hasClass("letter") ? ParaStyle.LETTER : ParaStyle.TEXT;
                        for (Element p : e.getElementsByTag("p")) blocks.add(paragraph(style, p, notes));
                    }
                }
                case "pre" -> {
                    // One paragraph per line, the formatting of each piece kept.
                    List<Inline> line = new ArrayList<>();
                    for (Inline i : inlines(e, notes, true)) {
                        if (i instanceof Inline.Text t && t.text().contains("\n")) {
                            String[] parts = t.text().split("\n", -1);
                            for (int k = 0; k < parts.length; k++) {
                                if (k > 0) {
                                    blocks.add(new Block.Paragraph(ParaStyle.CODE, line));
                                    line = new ArrayList<>();
                                }
                                line.add(new Inline.Text(parts[k], t.marks(), t.link()));
                            }
                        } else {
                            line.add(i);
                        }
                    }
                    blocks.add(new Block.Paragraph(ParaStyle.CODE, line));
                }
                case "figure" -> {
                    Element img = e.selectFirst("img");
                    if (img == null) continue;
                    String src = img.attr("src");
                    String name = src.substring(src.lastIndexOf('/') + 1);
                    Element caption = e.selectFirst("figcaption");
                    PictureSize size = e.hasClass("small") ? PictureSize.SMALL : e.hasClass("full") ? PictureSize.FULL
                            : PictureSize.MEDIUM;
                    blocks.add(new Block.Picture(name, img.attr("alt"), caption == null ? "" : caption.text(), size));
                }
                default -> {
                    if (!e.text().isBlank()) blocks.add(paragraph(ParaStyle.TEXT, e, notes));
                }
            }
        }
        return new Chapter(id, numbered, blocks);
    }
    // --8<-- [end:chapter]

    private static Block.Paragraph paragraph(ParaStyle style, Element e, Map<String, String> notes) {
        List<Inline> content = inlines(e, notes, false);
        // Trim the white space at both ends that the XHTML layout adds
        if (!content.isEmpty() && content.getFirst() instanceof Inline.Text t) {
            content.set(0, new Inline.Text(t.text().stripLeading(), t.marks(), t.link()));
        }
        if (!content.isEmpty() && content.getLast() instanceof Inline.Text t) {
            content.set(content.size() - 1, new Inline.Text(t.text().stripTrailing(), t.marks(), t.link()));
        }
        return new Block.Paragraph(style, content);
    }

    private static List<Inline> inlines(Element e, Map<String, String> notes, boolean pre) {
        List<Inline> out = new ArrayList<>();
        walk(e, EnumSet.noneOf(Mark.class), "", notes, pre, out);
        return out;
    }

    private static void walk(Node node, Set<Mark> marks, String link, Map<String, String> notes, boolean pre,
                             List<Inline> out) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode t) {
                String text = pre ? t.getWholeText() : t.getWholeText().replaceAll("\\s+", " ");
                if (!text.isEmpty()) out.add(new Inline.Text(text, marks, link));
            } else if (child instanceof Element e) {
                String tag = e.tagName();
                if (tag.equals("a") && e.attr("epub:type").contains("noteref")) {
                    String id = e.attr("href").replaceFirst("^.*#", "");
                    out.add(new Inline.Footnote(notes.getOrDefault(id, "")));
                    continue;
                }
                if (tag.equals("br")) {
                    out.add(new Inline.Text(" ", marks, link));
                    continue;
                }
                Set<Mark> inner = EnumSet.noneOf(Mark.class);
                inner.addAll(marks);
                String innerLink = link;
                switch (tag) {
                    case "strong", "b" -> inner.add(Mark.BOLD);
                    case "em", "i" -> inner.add(Mark.ITALIC);
                    case "sup" -> inner.add(Mark.SUPERSCRIPT);
                    case "sub" -> inner.add(Mark.SUBSCRIPT);
                    case "a" -> {
                        String href = e.attr("href");
                        Matcher m = CHAPTER_FILE.matcher(href.replaceFirst("#.*$", ""));
                        innerLink = m.matches() ? Link.chapter(m.group(1)) : href;
                    }
                    default -> {
                    }
                }
                walk(e, inner, innerLink, notes, pre, out);
            }
        }
    }
}
