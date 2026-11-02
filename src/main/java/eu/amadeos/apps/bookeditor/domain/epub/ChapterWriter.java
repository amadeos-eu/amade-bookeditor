package eu.amadeos.apps.bookeditor.domain.epub;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a chapter into an XHTML file of the book. Each paragraph style becomes one element; paragraphs of a
 * quote, poem, letter or code that follow each other share one element, poems split into stanzas at empty lines.
 */
public final class ChapterWriter {
    private final Book book;
    private final Chapter chapter;
    private final StringBuilder out = new StringBuilder();
    private final List<String> footnotes = new ArrayList<>();
    private int headings;

    private ChapterWriter(Book book, Chapter chapter) {
        this.book = book;
        this.chapter = chapter;
    }

    /** The file name of a chapter inside the {@code text} folder. */
    public static String fileName(Chapter chapter) {
        return chapter.id().equals(Book.DEDICATION_ID) ? "dedication.xhtml" : "chapter-" + chapter.id() + ".xhtml";
    }

    /** The id the n-th {@code h2} of a chapter gets, for the table of contents. */
    static String headingId(int n) {
        return "h-" + n;
    }

    // --8<-- [start:write]
    /** The whole XHTML file of the chapter, styled by {@code ../styles/book.css}. */
    public static String write(Book book, Chapter chapter) {
        return new ChapterWriter(book, chapter).file();
    }

    private String file() {
        String lang = Xml.attr(book.info().language());
        boolean dedication = chapter.id().equals(Book.DEDICATION_ID);
        BookTexts texts = BookTexts.of(book.info().language());
        String title = dedication ? texts.dedication() : chapter.title().isEmpty() ? book.info().title() : chapter.title();
        out.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" \
                xml:lang="%s" lang="%s">
                <head>
                <title>%s</title>
                <link rel="stylesheet" type="text/css" href="../styles/book.css"/>
                </head>
                """.formatted(lang, lang, Xml.text(title)));
        if (dedication) {
            out.append("<body epub:type=\"frontmatter\">\n");
            out.append("<section class=\"dedication\" epub:type=\"dedication\" role=\"doc-dedication\" aria-label=\"")
                    .append(Xml.attr(texts.dedication())).append("\">\n");
        } else {
            out.append("<body epub:type=\"bodymatter\">\n");
            out.append("<section class=\"chapter\" epub:type=\"chapter\" role=\"doc-chapter\">\n");
            int number = book.numberOf(chapter.id());
            if (number > 0) out.append("<p class=\"chapter-label\">").append(Xml.text(texts.chapter(number))).append("</p>\n");
        }
        blocks(chapter.blocks());
        out.append("</section>\n");
        for (int i = 0; i < footnotes.size(); i++) {
            int n = i + 1;
            out.append("<aside class=\"footnote\" epub:type=\"footnote\" role=\"doc-footnote\" id=\"fn-").append(n)
                    .append("\">\n<p><a href=\"#fnref-").append(n).append("\" role=\"doc-backlink\">").append(n)
                    .append(".</a> ").append(Xml.text(footnotes.get(i))).append("</p>\n</aside>\n");
        }
        out.append("</body>\n</html>\n");
        return out.toString();
    }
    // --8<-- [end:write]

    private void blocks(List<Block> blocks) {
        boolean afterText = false;
        int i = 0;
        while (i < blocks.size()) {
            Block block = blocks.get(i);
            switch (block) {
                case Block.SceneBreak ignored -> {
                    out.append("<hr class=\"scene-break\"/>\n");
                    afterText = false;
                    i++;
                }
                case Block.Picture p -> {
                    picture(p);
                    afterText = false;
                    i++;
                }
                case Block.Paragraph p -> {
                    ParaStyle style = p.style();
                    if (grouped(style)) {
                        int end = i;
                        while (end < blocks.size() && blocks.get(end) instanceof Block.Paragraph q && q.style() == style) end++;
                        List<Block.Paragraph> group = new ArrayList<>();
                        for (int k = i; k < end; k++) group.add((Block.Paragraph) blocks.get(k));
                        group(style, group);
                        afterText = false;
                        i = end;
                    } else {
                        afterText = paragraph(p, afterText);
                        i++;
                    }
                }
            }
        }
    }

    private static boolean grouped(ParaStyle style) {
        return style == ParaStyle.QUOTE || style == ParaStyle.EPIGRAPH || style == ParaStyle.POEM
                || style == ParaStyle.LETTER || style == ParaStyle.CODE;
    }

    /** Writes a single paragraph; returns whether it was body text (so the next one is indented). */
    private boolean paragraph(Block.Paragraph p, boolean afterText) {
        if (p.isEmpty()) return afterText;
        switch (p.style()) {
            case CHAPTER_TITLE -> element("h1", "", p);
            case HEADING -> element("h2", " id=\"" + headingId(++headings) + "\"", p);
            case SUBHEADING -> element("h3", "", p);
            case CENTERED -> element("p", " class=\"centered\"", p);
            default -> {
                element("p", afterText ? "" : " class=\"first\"", p);
                return true;
            }
        }
        return false;
    }

    private void element(String tag, String attributes, Block.Paragraph p) {
        out.append('<').append(tag).append(attributes).append('>');
        inlines(p.content());
        out.append("</").append(tag).append(">\n");
    }

    private void group(ParaStyle style, List<Block.Paragraph> lines) {
        switch (style) {
            case QUOTE -> wrap("<blockquote>\n", "</blockquote>\n", lines);
            case EPIGRAPH -> wrap("<blockquote class=\"epigraph\" epub:type=\"epigraph\" role=\"doc-epigraph\">\n",
                    "</blockquote>\n", lines);
            case LETTER -> wrap("<div class=\"letter\">\n", "</div>\n", lines);
            case CODE -> {
                // Empty lines stay inside the block; trailing ones are dropped.
                int last = lines.size();
                while (last > 0 && lines.get(last - 1).isEmpty()) last--;
                if (last == 0) return;
                out.append("<pre>");
                for (int k = 0; k < last; k++) {
                    if (k > 0) out.append('\n');
                    inlines(lines.get(k).content());
                }
                out.append("</pre>\n");
            }
            case POEM -> {
                List<List<Block.Paragraph>> stanzas = new ArrayList<>();
                List<Block.Paragraph> current = new ArrayList<>();
                for (Block.Paragraph line : lines) {
                    if (line.isEmpty()) {
                        if (!current.isEmpty()) stanzas.add(current);
                        current = new ArrayList<>();
                    } else {
                        current.add(line);
                    }
                }
                if (!current.isEmpty()) stanzas.add(current);
                if (stanzas.isEmpty()) return;
                out.append("<div class=\"poem\">\n");
                for (List<Block.Paragraph> stanza : stanzas) wrap("<div class=\"stanza\">\n", "</div>\n", stanza);
                out.append("</div>\n");
            }
            default -> throw new IllegalArgumentException(style.name());
        }
    }

    private void wrap(String open, String close, List<Block.Paragraph> lines) {
        if (lines.stream().allMatch(Block.Paragraph::isEmpty)) return;
        out.append(open);
        for (Block.Paragraph line : lines) {
            if (line.isEmpty()) continue;
            out.append("<p>");
            inlines(line.content());
            out.append("</p>\n");
        }
        out.append(close);
    }

    private void picture(Block.Picture p) {
        out.append("<figure class=\"picture ").append(p.size().key()).append("\">\n<img src=\"../images/")
                .append(Xml.attr(p.resource())).append("\" alt=\"").append(Xml.attr(p.description())).append("\"/>\n");
        if (!p.caption().isEmpty()) out.append("<figcaption>").append(Xml.text(p.caption())).append("</figcaption>\n");
        out.append("</figure>\n");
    }

    private void inlines(List<Inline> content) {
        for (Inline inline : content) {
            switch (inline) {
                case Inline.Footnote f -> {
                    footnotes.add(f.text());
                    int n = footnotes.size();
                    out.append("<a class=\"noteref\" epub:type=\"noteref\" role=\"doc-noteref\" id=\"fnref-").append(n)
                            .append("\" href=\"#fn-").append(n).append("\"><sup>").append(n).append("</sup></a>");
                }
                case Inline.Text t -> text(t);
            }
        }
    }

    private void text(Inline.Text t) {
        List<String> close = new ArrayList<>();
        if (!t.link().isEmpty()) {
            String href = Link.chapterId(t.link()).map(id -> "chapter-" + id + ".xhtml").orElse(t.link());
            out.append("<a href=\"").append(Xml.attr(href)).append("\">");
            close.addFirst("</a>");
        }
        for (Mark m : Mark.values()) {
            if (!t.has(m)) continue;
            String tag = switch (m) {
                case BOLD -> "strong";
                case ITALIC -> "em";
                case SUPERSCRIPT -> "sup";
                case SUBSCRIPT -> "sub";
            };
            out.append('<').append(tag).append('>');
            close.addFirst("</" + tag + ">");
        }
        out.append(Xml.text(t.text()));
        close.forEach(out::append);
    }
}
