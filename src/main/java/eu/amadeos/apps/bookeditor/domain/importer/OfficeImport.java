package eu.amadeos.apps.bookeditor.domain.importer;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.KeptKind;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// --8<-- [start:intro]
/**
 * Starts a book from a word processor document: OpenDocument Text (.odt) or Word (.docx). Older formats (.doc,
 * .rtf) come here after LibreOffice turned them into OpenDocument.
 * <p>
 * The document's headings make the chapters: the highest heading level used more than once starts a chapter,
 * the next ones are headings and subheadings. Bold, italic, superscript, subscript, links, footnotes and
 * endnotes, pictures (with their descriptions), quotes, centred and preformatted paragraphs come along; "* * *"
 * is a scene break; list items become paragraphs starting with "•" or their number; tables are kept as tables.
 * Fonts, sizes, colours and page settings are left behind: the book's look decides those.
 */
// --8<-- [end:intro]
public final class OfficeImport {
    private static final Pattern SCENE_BREAK = Pattern.compile("^[\\s*•·~#⁂§❦❧✻✽✶◆◇○●–—-]{1,20}$");

    private OfficeImport() {
    }

    /** Reads an ODT or DOCX file into chapters; its pictures are added to {@code book}. */
    public static TextImport.Result read(byte[] file, String extension, Book book) throws IOException {
        Map<String, byte[]> parts = unzip(file);
        List<Object> raw = switch (extension.toLowerCase(Locale.ROOT)) {
            case "odt" -> new Odt(parts, book).read();
            case "docx" -> new Docx(parts, book).read();
            default -> throw new IOException("not a word processor document: ." + extension);
        };
        return new TextImport.Result(chapters(raw), List.of());
    }

    private static Map<String, byte[]> unzip(byte[] file) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) parts.put(e.getName(), zip.readAllBytes());
            }
        }
        if (parts.isEmpty()) throw new IOException("not a zip file");
        return parts;
    }

    private static Document xml(byte[] bytes) {
        Document doc = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    /** Raw items are blocks or {@link HeadingItem}s; this decides the chapter level and splits into chapters. */
    static List<Chapter> chapters(List<Object> raw) {
        int[] count = new int[10];
        for (Object o : raw) if (o instanceof HeadingItem h) count[Math.min(9, h.level())]++;
        int top = 1;
        boolean found = false;
        for (int l = 1; l <= 9 && !found; l++) if (count[l] >= 2) {
            top = l;
            found = true;
        }
        for (int l = 1; l <= 9 && !found; l++) if (count[l] >= 1) {
            top = l;
            found = true;
        }
        List<Chapter> chapters = new ArrayList<>();
        List<Block> current = null;
        for (Object o : raw) {
            Block b;
            if (o instanceof TitleItem t) {
                // The document's own title block: the editor makes a title page, so only later ones stay
                if (current == null) continue;
                b = new Block.Paragraph(ParaStyle.CENTERED, t.content());
            } else if (o instanceof HeadingItem h) {
                if (h.level() == top) {
                    if (current != null) chapters.add(new Chapter(Chapter.newId(), false, current));
                    current = new ArrayList<>();
                    current.add(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, h.content()));
                    continue;
                }
                b = new Block.Paragraph(h.level() < top ? ParaStyle.CENTERED : h.level() == top + 1 ? ParaStyle.HEADING
                        : ParaStyle.SUBHEADING, h.content());
            } else {
                b = (Block) o;
            }
            if (current == null) {
                current = new ArrayList<>();
                current.add(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, ""));
            }
            current.add(b);
        }
        if (current != null) chapters.add(new Chapter(Chapter.newId(), false, current));
        if (chapters.isEmpty()) chapters.add(Chapter.create(""));
        return chapters;
    }

    /** A heading of a level (1 for "Heading 1") with its text. */
    record HeadingItem(int level, List<Inline> content) {
    }

    /** A paragraph in a title, subtitle or author style. */
    record TitleItem(List<Inline> content) {
    }

    /** What a paragraph style stands for, from its name. */
    enum Role {
        TEXT, QUOTE, CODE, CENTERED, TITLE, CAPTION, LINE;

        /** From the names of a style and the styles it is based on, nearest first. */
        static Role of(List<String> names, boolean centered) {
            for (String raw : names) {
                String n = raw.toLowerCase(Locale.ROOT).replace("_20_", " ").replace('_', ' ').strip();
                if (n.matches("(sub)?title|author|date|abstract|(unter)?titel|autor|datum|titre|sous-titre|titolo|sottotitolo|t[ií]tulo|subt[ií]tulo")) {
                    return TITLE;
                }
                if (n.equals("horizontal line") || n.equals("horizontalline")) return LINE;
                if (n.contains("caption") || n.contains("beschriftung") || n.contains("legende") || n.contains("didascalia")
                        || n.equals("illustration") || n.equals("figure") || n.equals("abbildung")) {
                    return CAPTION;
                }
                if (n.contains("quot") || n.contains("zitat") || n.contains("citation") || n.contains("block text")
                        || n.contains("cita")) {
                    return QUOTE;
                }
                if (n.contains("preformatted") || n.contains("source text") || n.contains("code") || n.contains("quelltext")) {
                    return CODE;
                }
            }
            return centered ? CENTERED : TEXT;
        }

        ParaStyle style() {
            return switch (this) {
                case QUOTE -> ParaStyle.QUOTE;
                case CODE -> ParaStyle.CODE;
                case CENTERED, TITLE, CAPTION, LINE -> ParaStyle.CENTERED;
                case TEXT -> ParaStyle.TEXT;
            };
        }
    }

    // ---------------------------------------------------------------- shared

    /** Collects the inline content of one paragraph and the pictures in it. */
    private static final class Line {
        final List<Inline> content = new ArrayList<>();
        final List<Block.Picture> pictures = new ArrayList<>();

        void text(String text, Set<Mark> marks, String link) {
            if (!text.isEmpty()) content.add(new Inline.Text(text, marks, link));
        }

        List<Inline> trimmed() {
            List<Inline> c = new ArrayList<>(content);
            for (int i = 1; i < c.size(); i++) {
                if (c.get(i - 1) instanceof Inline.Text a && c.get(i) instanceof Inline.Text b && a.text().endsWith(" ")
                        && b.text().startsWith(" ")) {
                    c.set(i, new Inline.Text(b.text().substring(1), b.marks(), b.link()));
                }
            }
            c.removeIf(i -> i instanceof Inline.Text t && t.text().isEmpty());
            while (!c.isEmpty() && c.getFirst() instanceof Inline.Text t && t.text().isBlank()) c.removeFirst();
            while (!c.isEmpty() && c.getLast() instanceof Inline.Text t && t.text().isBlank()) c.removeLast();
            if (!c.isEmpty() && c.getFirst() instanceof Inline.Text t) {
                c.set(0, new Inline.Text(t.text().stripLeading(), t.marks(), t.link()));
            }
            if (!c.isEmpty() && c.getLast() instanceof Inline.Text t) {
                c.set(c.size() - 1, new Inline.Text(t.text().stripTrailing(), t.marks(), t.link()));
            }
            return c;
        }

        String plain() {
            StringBuilder sb = new StringBuilder();
            for (Inline i : content) if (i instanceof Inline.Text t) sb.append(t.text());
            return sb.toString().strip();
        }
    }

    /** Adds a paragraph (or a scene break, or a heading) and the pictures that were in it. */
    private static void add(List<Object> out, Line line, Role role, int headingLevel, String prefix) {
        List<Inline> content = line.trimmed();
        if (!prefix.isEmpty() && !content.isEmpty()) content.addFirst(new Inline.Text(prefix));
        String plain = line.plain();
        if (headingLevel > 0) {
            if (!content.isEmpty()) out.add(new HeadingItem(headingLevel, content));
        } else if (!plain.isEmpty() && SCENE_BREAK.matcher(plain).matches() && line.pictures.isEmpty()
                && content.stream().noneMatch(i -> i instanceof Inline.Footnote)) {
            out.add(new Block.SceneBreak());
        } else if (role == Role.LINE && plain.isEmpty() && line.pictures.isEmpty()) {
            out.add(new Block.SceneBreak());
        } else if (content.isEmpty()) {
            // nothing but perhaps pictures
        } else if (role == Role.TITLE) {
            out.add(new TitleItem(content));
        } else if (role == Role.CAPTION && line.pictures.isEmpty() && !out.isEmpty()
                && out.getLast() instanceof Block.Picture pic && pic.caption().isEmpty()) {
            out.set(out.size() - 1, new Block.Picture(pic.resource(), pic.description(), plain, pic.size()));
        } else {
            out.add(new Block.Paragraph(role.style(), content));
        }
        out.addAll(line.pictures);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** A table as kept XHTML: rows and cells with their plain text. */
    private static Block.Kept table(List<List<String>> rows) {
        StringBuilder x = new StringBuilder("<table>");
        StringBuilder text = new StringBuilder();
        for (List<String> row : rows) {
            x.append("<tr>");
            for (String cell : row) {
                x.append("<td>").append(escape(cell)).append("</td>");
                text.append(cell).append(' ');
            }
            x.append("</tr>");
        }
        x.append("</table>");
        return new Block.Kept(KeptKind.TABLE, x.toString(), text.toString());
    }

    private static String pictureName(Book book, Map<String, byte[]> parts, String path, String fallback) {
        byte[] data = parts.get(path);
        if (data == null) return null;
        String name = path.substring(path.lastIndexOf('/') + 1);
        String type = Resource.mediaTypeOf(name);
        if (type.isEmpty()) return null;
        return book.addResource(name.isEmpty() ? fallback : name, type, data);
    }

    private static Set<Mark> with(Set<Mark> marks, Set<Mark> more) {
        Set<Mark> s = EnumSet.noneOf(Mark.class);
        s.addAll(marks);
        s.addAll(more);
        return s;
    }

    // ---------------------------------------------------------------- OpenDocument

    /** Reads {@code content.xml}, with the named styles of {@code styles.xml}. */
    private static final class Odt {
        private record Style(String parent, String displayName, Set<Mark> marks, Set<Mark> off, boolean centered,
                             int outline, boolean numbered) {
        }

        private final Map<String, byte[]> parts;
        private final Book book;
        private final Map<String, Style> styles = new HashMap<>();
        private final Map<String, Boolean> numberedLists = new HashMap<>();
        private final Set<String> automatic = new java.util.HashSet<>();
        private final List<Object> out = new ArrayList<>();

        Odt(Map<String, byte[]> parts, Book book) {
            this.parts = parts;
            this.book = book;
        }

        List<Object> read() throws IOException {
            byte[] content = parts.get("content.xml");
            if (content == null) throw new IOException("no content.xml: not an OpenDocument text");
            if (parts.containsKey("styles.xml")) styles(xml(parts.get("styles.xml")));
            Document doc = xml(content);
            styles(doc);
            Element text = doc.selectFirst("office|text");
            if (text == null) throw new IOException("no text in the document");
            blocks(text, 0);
            return out;
        }

        private void styles(Document doc) {
            for (Element s : doc.getElementsByTag("style:style")) {
                Set<Mark> on = EnumSet.noneOf(Mark.class), off = EnumSet.noneOf(Mark.class);
                boolean centered = false;
                Element tp = s.selectFirst("style|text-properties");
                if (tp != null) {
                    String weight = tp.attr("fo:font-weight");
                    if (weight.equals("bold") || weight.matches("[6-9]00")) on.add(Mark.BOLD);
                    else if (weight.equals("normal")) off.add(Mark.BOLD);
                    String style = tp.attr("fo:font-style");
                    if (style.equals("italic") || style.equals("oblique")) on.add(Mark.ITALIC);
                    else if (style.equals("normal")) off.add(Mark.ITALIC);
                    String pos = tp.attr("style:text-position");
                    if (pos.startsWith("super") || pos.matches("\\d.*")) on.add(Mark.SUPERSCRIPT);
                    else if (pos.startsWith("sub") || pos.startsWith("-")) on.add(Mark.SUBSCRIPT);
                }
                Element pp = s.selectFirst("style|paragraph-properties");
                if (pp != null) centered = pp.attr("fo:text-align").equals("center");
                int outline = 0;
                try {
                    if (s.hasAttr("style:default-outline-level")) outline = Integer.parseInt(s.attr("style:default-outline-level"));
                } catch (NumberFormatException ignored) {
                    outline = 0;
                }
                if (s.parent() != null && s.parent().tagName().equals("office:automatic-styles")) automatic.add(s.attr("style:name"));
                styles.put(s.attr("style:name"), new Style(s.attr("style:parent-style-name"),
                        s.attr("style:display-name").isEmpty() ? s.attr("style:name") : s.attr("style:display-name"),
                        on, off, centered, outline, false));
            }
            for (Element l : doc.getElementsByTag("text:list-style")) {
                Element first = l.children().isEmpty() ? null : l.child(0);
                numberedLists.put(l.attr("style:name"), first != null && first.tagName().equals("text:list-level-style-number")
                        && !first.attr("style:num-format").isEmpty());
            }
        }

        /**
         * The marks of a paragraph's own formatting (its automatic style), not those of its named style: a heading
         * style's bold or a quote style's italic is the look's business.
         */
        private Set<Mark> directMarks(String name) {
            Style s = styles.get(name);
            if (s == null || !automatic.contains(name)) return EnumSet.noneOf(Mark.class);
            Set<Mark> marks = EnumSet.noneOf(Mark.class);
            marks.addAll(s.marks());
            return marks;
        }

        /** The marks a style sets, its parents' included. */
        private Set<Mark> marks(String name) {
            Set<Mark> marks = EnumSet.noneOf(Mark.class);
            List<Style> chain = chain(name);
            for (int i = chain.size() - 1; i >= 0; i--) {
                marks.addAll(chain.get(i).marks());
                marks.removeAll(chain.get(i).off());
            }
            return marks;
        }

        private List<Style> chain(String name) {
            List<Style> chain = new ArrayList<>();
            String n = name;
            for (int i = 0; i < 20 && n != null && !n.isEmpty(); i++) {
                Style s = styles.get(n);
                if (s == null) break;
                chain.add(s);
                n = s.parent();
            }
            return chain;
        }

        private Role role(String name) {
            List<String> names = new ArrayList<>();
            names.add(name);
            for (Style st : chain(name)) names.add(st.displayName());
            return Role.of(names, chain(name).stream().anyMatch(Style::centered));
        }

        private void blocks(Element parent, int depth) {
            for (Element e : parent.children()) {
                switch (e.tagName()) {
                    case "text:h" -> {
                        int level;
                        try {
                            level = Integer.parseInt(e.attr("text:outline-level"));
                        } catch (NumberFormatException x) {
                            level = 1;
                        }
                        paragraph(e, Role.TEXT, level, "");
                    }
                    case "text:p" -> {
                        int outline = chain(e.attr("text:style-name")).stream().mapToInt(Style::outline).filter(i -> i > 0)
                                .findFirst().orElse(0);
                        paragraph(e, role(e.attr("text:style-name")), outline, "");
                    }
                    case "text:list" -> list(e, depth, numberedLists.getOrDefault(e.attr("text:style-name"), false));
                    case "table:table" -> table(e);
                    case "text:section", "text:soft-page-break" -> blocks(e, depth);
                    default -> {
                        // table of contents, indexes, forms, settings: left out
                    }
                }
            }
        }

        private void list(Element list, int depth, boolean numbered) {
            int n = 0;
            for (Element item : list.children()) {
                if (!item.tagName().equals("text:list-item") && !item.tagName().equals("text:list-header")) continue;
                n++;
                boolean first = true;
                for (Element c : item.children()) {
                    if (c.tagName().equals("text:list")) {
                        list(c, depth + 1, numberedLists.getOrDefault(c.attr("text:style-name"), numbered));
                    } else if (c.tagName().equals("text:p") || c.tagName().equals("text:h")) {
                        String prefix = !first || item.tagName().equals("text:list-header") ? ""
                                : "  ".repeat(depth) + (numbered ? n + ". " : "• ");
                        paragraph(c, Role.TEXT, 0, prefix);
                        first = false;
                    }
                }
            }
        }

        private void table(Element t) {
            List<List<String>> rows = new ArrayList<>();
            for (Element row : t.select("table|table-row")) {
                List<String> cells = new ArrayList<>();
                for (Element cell : row.children()) {
                    if (cell.tagName().equals("table:table-cell")) cells.add(cell.text().strip());
                }
                if (cells.stream().anyMatch(c -> !c.isEmpty())) rows.add(cells);
            }
            if (!rows.isEmpty()) out.add(OfficeImport.table(rows));
        }

        private void paragraph(Element p, Role role, int heading, String prefix) {
            Line line = new Line();
            inline(p, directMarks(p.attr("text:style-name")), "", line);
            add(out, line, role, heading, prefix);
        }

        private void inline(Element e, Set<Mark> marks, String link, Line line) {
            for (Node n : e.childNodes()) {
                if (n instanceof TextNode t) {
                    line.text(t.getWholeText().replaceAll("\\s+", " "), marks, link);
                    continue;
                }
                if (!(n instanceof Element c)) continue;
                switch (c.tagName()) {
                    case "text:span" -> inline(c, with(marks, marks(c.attr("text:style-name"))), link, line);
                    case "text:a" -> {
                        String href = c.attr("xlink:href");
                        inline(c, marks, Link.web(href) ? href : link, line);
                    }
                    case "text:s" -> line.text(" ", marks, link);
                    case "text:tab", "text:line-break" -> line.text(" ", marks, link);
                    case "text:note" -> {
                        Element body = c.selectFirst("text|note-body");
                        line.content.add(new Inline.Footnote(body == null ? "" : body.text()));
                    }
                    case "draw:frame" -> frame(c, line);
                    case "text:bookmark", "text:bookmark-start", "text:bookmark-end", "text:soft-page-break",
                         "office:annotation", "office:annotation-end", "text:change", "text:change-start",
                         "text:change-end" -> {
                    }
                    default -> inline(c, marks, link, line);
                }
            }
        }

        private void frame(Element frame, Line line) {
            Element img = frame.selectFirst("draw|image");
            if (img == null) {
                // A text frame: its paragraphs' text joins the line
                Element box = frame.selectFirst("draw|text-box");
                if (box != null) for (Element p : box.select("text|p")) line.text(" " + p.text() + " ", Set.of(), "");
                return;
            }
            String name = pictureName(book, parts, img.attr("xlink:href"), "picture.png");
            if (name == null) return;
            Element title = frame.selectFirst("svg|title");
            Element desc = frame.selectFirst("svg|desc");
            String description = desc != null && !desc.text().isBlank() ? desc.text() : title != null ? title.text() : "";
            line.pictures.add(new Block.Picture(name, description, "", PictureSize.MEDIUM));
        }
    }

    // ---------------------------------------------------------------- Word

    /** Reads {@code word/document.xml}, with styles, numbering, footnotes and relationships. */
    private static final class Docx {
        private record Style(String name, String basedOn, Set<Mark> marks, Set<Mark> off, boolean centered,
                             int outline) {
        }

        private final Map<String, byte[]> parts;
        private final Book book;
        private final Map<String, Style> styles = new HashMap<>();
        private final Map<String, String> relations = new HashMap<>();
        private final Map<String, String> notes = new HashMap<>();
        private final Map<String, String> endnotes = new HashMap<>();
        /** Whether a list (numId) at level 0 counts 1, 2, 3; the counters while reading. */
        private final Map<String, Boolean> numbered = new HashMap<>();
        private final Map<String, Integer> counters = new HashMap<>();
        private final List<Object> out = new ArrayList<>();

        Docx(Map<String, byte[]> parts, Book book) {
            this.parts = parts;
            this.book = book;
        }

        List<Object> read() throws IOException {
            byte[] document = parts.get("word/document.xml");
            if (document == null) throw new IOException("no word/document.xml: not a Word document");
            if (parts.containsKey("word/styles.xml")) styles(xml(parts.get("word/styles.xml")));
            if (parts.containsKey("word/_rels/document.xml.rels")) {
                for (Element r : xml(parts.get("word/_rels/document.xml.rels")).getElementsByTag("Relationship")) {
                    String target = r.attr("Target");
                    if (!r.attr("TargetMode").equals("External")) {
                        target = target.startsWith("/") ? target.substring(1) : "word/" + target;
                    }
                    relations.put(r.attr("Id"), target);
                }
            }
            if (parts.containsKey("word/numbering.xml")) numbering(xml(parts.get("word/numbering.xml")));
            notes("word/footnotes.xml", "w:footnote", notes);
            notes("word/endnotes.xml", "w:endnote", endnotes);
            Element body = xml(document).selectFirst("w|body");
            if (body == null) throw new IOException("no body in the document");
            blocks(body);
            return out;
        }

        private static boolean on(Element toggle) {
            if (toggle == null) return false;
            String v = toggle.attr("w:val");
            return v.isEmpty() || v.equals("true") || v.equals("1") || v.equals("on");
        }

        private static void runMarks(Element rPr, Set<Mark> on, Set<Mark> off) {
            if (rPr == null) return;
            for (String[] t : new String[][]{{"w:b", "BOLD"}, {"w:i", "ITALIC"}}) {
                Element x = rPr.getElementsByTag(t[0]).first();
                if (x == null || x.parent() != rPr) continue;
                (on(x) ? on : off).add(Mark.valueOf(t[1]));
            }
            Element va = rPr.getElementsByTag("w:vertAlign").first();
            if (va != null && va.attr("w:val").equals("superscript")) on.add(Mark.SUPERSCRIPT);
            if (va != null && va.attr("w:val").equals("subscript")) on.add(Mark.SUBSCRIPT);
        }

        private void styles(Document doc) {
            for (Element s : doc.getElementsByTag("w:style")) {
                Set<Mark> on = EnumSet.noneOf(Mark.class), off = EnumSet.noneOf(Mark.class);
                Element rPr = child(s, "w:rPr");
                runMarks(rPr, on, off);
                Element pPr = child(s, "w:pPr");
                boolean centered = false;
                int outline = 0;
                if (pPr != null) {
                    Element jc = child(pPr, "w:jc");
                    centered = jc != null && jc.attr("w:val").equals("center");
                    Element ol = child(pPr, "w:outlineLvl");
                    if (ol != null) {
                        try {
                            int v = Integer.parseInt(ol.attr("w:val"));
                            if (v < 9) outline = v + 1;
                        } catch (NumberFormatException ignored) {
                            outline = 0;
                        }
                    }
                }
                Element name = child(s, "w:name");
                Element based = child(s, "w:basedOn");
                String n = name == null ? s.attr("w:styleId") : name.attr("w:val");
                var m = Pattern.compile("(?i)^heading\\s*(\\d)$").matcher(n.strip());
                if (outline == 0 && m.matches()) outline = Integer.parseInt(m.group(1));
                styles.put(s.attr("w:styleId"), new Style(n, based == null ? "" : based.attr("w:val"), on, off, centered, outline));
            }
        }

        private static Element child(Element parent, String tag) {
            for (Element c : parent.children()) if (c.tagName().equals(tag)) return c;
            return null;
        }

        private List<Style> chain(String id) {
            List<Style> chain = new ArrayList<>();
            String n = id;
            for (int i = 0; i < 20 && n != null && !n.isEmpty(); i++) {
                Style s = styles.get(n);
                if (s == null) break;
                chain.add(s);
                n = s.basedOn();
            }
            return chain;
        }

        private Set<Mark> styleMarks(String id) {
            Set<Mark> marks = EnumSet.noneOf(Mark.class);
            List<Style> chain = chain(id);
            for (int i = chain.size() - 1; i >= 0; i--) {
                marks.addAll(chain.get(i).marks());
                marks.removeAll(chain.get(i).off());
            }
            return marks;
        }

        private void numbering(Document doc) {
            Map<String, Boolean> abstractNumbered = new HashMap<>();
            for (Element a : doc.getElementsByTag("w:abstractNum")) {
                Element lvl = a.selectFirst("w|lvl[w:ilvl=0]");
                Element fmt = lvl == null ? null : lvl.selectFirst("w|numFmt");
                abstractNumbered.put(a.attr("w:abstractNumId"), fmt != null && !fmt.attr("w:val").equals("bullet")
                        && !fmt.attr("w:val").equals("none"));
            }
            for (Element n : doc.getElementsByTag("w:num")) {
                Element a = n.selectFirst("w|abstractNumId");
                if (a != null) numbered.put(n.attr("w:numId"), abstractNumbered.getOrDefault(a.attr("w:val"), false));
            }
        }

        private void notes(String part, String tag, Map<String, String> into) {
            if (!parts.containsKey(part)) return;
            for (Element n : xml(parts.get(part)).getElementsByTag(tag)) {
                String type = n.attr("w:type");
                if (type.equals("separator") || type.equals("continuationSeparator")) continue;
                StringBuilder sb = new StringBuilder();
                for (Element p : n.getElementsByTag("w:p")) {
                    StringBuilder line = new StringBuilder();
                    for (Element t : p.select("w|t, w|tab")) line.append(t.tagName().equals("w:tab") ? " " : t.wholeText());
                    if (!line.isEmpty()) sb.append(sb.isEmpty() ? "" : " ").append(line);
                }
                into.put(n.attr("w:id"), sb.toString().strip());
            }
        }

        private void blocks(Element parent) {
            for (Element e : parent.children()) {
                switch (e.tagName()) {
                    case "w:p" -> paragraph(e);
                    case "w:tbl" -> table(e);
                    case "w:sdt" -> {
                        Element gallery = e.selectFirst("w|sdtPr w|docPartGallery");
                        if (gallery != null && gallery.attr("w:val").toLowerCase(Locale.ROOT).contains("table of contents")) continue;
                        Element content = child(e, "w:sdtContent");
                        if (content != null) blocks(content);
                    }
                    case "w:customXml", "w:ins", "w:smartTag" -> blocks(e);
                    default -> {
                    }
                }
            }
        }

        private void table(Element t) {
            List<List<String>> rows = new ArrayList<>();
            for (Element row : t.children()) {
                if (!row.tagName().equals("w:tr")) continue;
                List<String> cells = new ArrayList<>();
                for (Element cell : row.children()) {
                    if (!cell.tagName().equals("w:tc")) continue;
                    StringBuilder sb = new StringBuilder();
                    for (Element tt : cell.select("w|t")) sb.append(tt.wholeText());
                    cells.add(sb.toString().strip());
                }
                if (cells.stream().anyMatch(c -> !c.isEmpty())) rows.add(cells);
            }
            if (!rows.isEmpty()) out.add(OfficeImport.table(rows));
        }

        private void paragraph(Element p) {
            Element pPr = child(p, "w:pPr");
            String styleId = "";
            boolean centered = false;
            int outline = 0;
            String prefix = "";
            if (pPr != null) {
                Element ps = child(pPr, "w:pStyle");
                if (ps != null) styleId = ps.attr("w:val");
                Element jc = child(pPr, "w:jc");
                centered = jc != null && jc.attr("w:val").equals("center");
                Element ol = child(pPr, "w:outlineLvl");
                if (ol != null) {
                    try {
                        int v = Integer.parseInt(ol.attr("w:val"));
                        if (v < 9) outline = v + 1;
                    } catch (NumberFormatException ignored) {
                        outline = 0;
                    }
                }
                Element num = child(pPr, "w:numPr");
                if (num != null) {
                    Element id = num.selectFirst("w|numId");
                    Element lvl = num.selectFirst("w|ilvl");
                    String numId = id == null ? "" : id.attr("w:val");
                    int level = lvl == null ? 0 : Math.max(0, parseInt(lvl.attr("w:val")));
                    if (!numId.isEmpty() && !numId.equals("0")) {
                        boolean counts = numbered.getOrDefault(numId, false) && level == 0;
                        int n = counters.merge(numId, level == 0 ? 1 : 0, Integer::sum);
                        prefix = "  ".repeat(level) + (counts ? n + ". " : "• ");
                    }
                }
            }
            List<Style> chain = chain(styleId);
            if (outline == 0) outline = chain.stream().mapToInt(Style::outline).filter(i -> i > 0).findFirst().orElse(0);
            List<String> names = new ArrayList<>();
            names.add(styleId);
            for (Style st : chain) names.add(st.name());
            Role role = Role.of(names, centered || chain.stream().anyMatch(Style::centered));
            if (p.selectFirst("v|rect[o:hr]") != null) role = Role.LINE;
            Line line = new Line();
            // The paragraph style's own bold or italic is the look's business; only direct formatting counts
            runs(p, EnumSet.noneOf(Mark.class), "", line);
            add(out, line, role, outline, prefix);
        }

        private static int parseInt(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private void runs(Element parent, Set<Mark> base, String link, Line line) {
            for (Element e : parent.children()) {
                switch (e.tagName()) {
                    case "w:r" -> run(e, base, link, line);
                    case "w:hyperlink" -> {
                        String target = relations.getOrDefault(e.attr("r:id"), "");
                        runs(e, base, Link.web(target) ? target : link, line);
                    }
                    case "w:ins", "w:smartTag", "w:fldSimple", "w:customXml", "w:bdo", "w:dir" -> runs(e, base, link, line);
                    case "w:sdt" -> {
                        Element content = child(e, "w:sdtContent");
                        if (content != null) runs(content, base, link, line);
                    }
                    default -> {
                        // w:del, w:pPr, bookmarks, comments: left out
                    }
                }
            }
        }

        private void run(Element r, Set<Mark> base, String link, Line line) {
            Set<Mark> marks = EnumSet.noneOf(Mark.class);
            marks.addAll(base);
            Element rPr = child(r, "w:rPr");
            if (rPr != null) {
                Element rs = child(rPr, "w:rStyle");
                if (rs != null) marks.addAll(styleMarks(rs.attr("w:val")));
                Set<Mark> on = EnumSet.noneOf(Mark.class), off = EnumSet.noneOf(Mark.class);
                runMarks(rPr, on, off);
                marks.addAll(on);
                marks.removeAll(off);
            }
            for (Element c : r.children()) {
                switch (c.tagName()) {
                    case "w:t" -> line.text(c.wholeText(), marks, link);
                    case "w:tab", "w:noBreakHyphen" -> line.text(c.tagName().equals("w:tab") ? " " : "-", marks, link);
                    case "w:br", "w:cr" -> {
                        if (!c.attr("w:type").equals("page")) line.text(" ", marks, link);
                    }
                    case "w:footnoteReference" -> line.content.add(new Inline.Footnote(notes.getOrDefault(c.attr("w:id"), "")));
                    case "w:endnoteReference" -> line.content.add(new Inline.Footnote(endnotes.getOrDefault(c.attr("w:id"), "")));
                    case "w:drawing", "mc:AlternateContent", "w:pict" -> drawing(c, line);
                    default -> {
                    }
                }
            }
        }

        private void drawing(Element d, Line line) {
            Element blip = d.selectFirst("a|blip");
            String id = blip == null ? "" : blip.attr("r:embed");
            if (id.isEmpty()) {
                Element v = d.selectFirst("v|imagedata");
                id = v == null ? "" : v.attr("r:id");
            }
            String path = relations.get(id);
            if (path == null) return;
            String name = pictureName(book, parts, path, "picture.png");
            if (name == null) return;
            Element pr = d.selectFirst("wp|docPr");
            String description = pr == null ? "" : !pr.attr("descr").isBlank() ? pr.attr("descr") : pr.attr("title");
            line.pictures.add(new Block.Picture(name, description, "", PictureSize.MEDIUM));
        }
    }
}
