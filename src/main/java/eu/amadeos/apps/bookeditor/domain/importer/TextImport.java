package eu.amadeos.apps.bookeditor.domain.importer;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Resource;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starts a book from a plain text or Markdown file.
 * <p>
 * Plain text: paragraphs are separated by empty lines; a line such as "Chapter 3" or "Kapitel 3" (alone or with a
 * title after it) starts a chapter; "* * *" is a scene break.
 * <p>
 * Markdown: {@code #} starts a chapter, {@code ##} and {@code ###} are headings, {@code >} quotes,
 * {@code ***} or {@code ---} scene breaks, {@code *italic*}, {@code **bold**}, {@code [text](https://…)} links,
 * {@code ![description](picture.png "caption")} pictures (next to the file) and {@code [^1]} footnotes.
 */
public final class TextImport {
    private static final Pattern CHAPTER_LINE = Pattern.compile(
            "(?i)^(chapter|kapitel|chapitre|capitolo|cap[ií]tulo|hoofdstuk)\\s+([0-9]+|[ivxlcdm]+|[\\p{L}]+)\\b[.:]?\\s*(.*)$");
    private static final Pattern SCENE_BREAK = Pattern.compile("^\\s*(\\*\\s*\\*\\s*\\*[\\s*]*|-{3,}|_{3,}|#\\s*#\\s*#)\\s*$");
    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern MD_PICTURE = Pattern.compile("^!\\[([^]]*)]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"([^\"]*)\")?\\s*\\)\\s*$");
    private static final Pattern MD_NOTE_DEF = Pattern.compile("^\\[\\^([^]]+)]:\\s*(.*)$");
    private static final Pattern MD_INLINE = Pattern.compile(
            "\\[\\^([^]]+)]|\\[([^]]+)]\\(([^)\\s]+)\\)|\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*|_(.+?)_|\\\\(.)");

    /** What came in: chapters, and the pictures that could not be found next to the file. */
    public record Result(List<Chapter> chapters, List<String> missingPictures) {
    }

    private TextImport() {
    }

    public static boolean markdown(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    /** Reads the file into chapters; pictures of a Markdown file are added to {@code book}. */
    public static Result read(Path file, Book book) throws IOException {
        String text = readText(file);
        return markdown(file) ? markdown(text, file.toAbsolutePath().getParent(), book) : plain(text);
    }

    /** UTF-8, or Windows-1252 for older files that are not. */
    static String readText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    // ---------------------------------------------------------------- plain text

    public static Result plain(String text) {
        Builder b = new Builder();
        for (String para : text.split("\n\\s*\n")) {
            String p = para.strip();
            if (p.isEmpty()) continue;
            String[] lines = p.split("\n");
            Matcher m = CHAPTER_LINE.matcher(lines[0].strip());
            if (m.matches() && lines[0].length() < 80) {
                String title = m.group(3).strip();
                if (title.isEmpty() && lines.length > 1) {
                    title = lines[1].strip();
                    lines = java.util.Arrays.copyOfRange(lines, 1, lines.length);
                }
                b.chapter(title.isEmpty() ? lines[0].strip() : title);
                if (lines.length > 1) b.add(new Block.Paragraph(ParaStyle.TEXT, String.join(" ", rest(lines)).strip()));
                continue;
            }
            if (SCENE_BREAK.matcher(p).matches()) {
                b.add(new Block.SceneBreak());
                continue;
            }
            b.add(new Block.Paragraph(ParaStyle.TEXT, p.replaceAll("\\s*\n\\s*", " ")));
        }
        return new Result(b.finish(), List.of());
    }

    private static String[] rest(String[] lines) {
        return java.util.Arrays.copyOfRange(lines, 1, lines.length);
    }

    // ---------------------------------------------------------------- Markdown

    public static Result markdown(String text, Path folder, Book book) {
        Map<String, String> notes = new HashMap<>();
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            Matcher m = MD_NOTE_DEF.matcher(line);
            if (m.matches()) notes.put(m.group(1), m.group(2).strip());
            else lines.add(line);
        }
        Builder b = new Builder();
        List<String> missing = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        ParaStyle paraStyle = ParaStyle.TEXT;
        boolean code = false;
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.strip().startsWith("```")) {
                b.flush(para, paraStyle, notes);
                code = !code;
                continue;
            }
            if (code) {
                b.add(new Block.Paragraph(ParaStyle.CODE, line));
                continue;
            }
            if (line.isBlank()) {
                b.flush(para, paraStyle, notes);
                paraStyle = ParaStyle.TEXT;
                continue;
            }
            Matcher h = MD_HEADING.matcher(line);
            if (h.matches()) {
                b.flush(para, paraStyle, notes);
                int level = h.group(1).length();
                if (level == 1) b.chapter("");
                ParaStyle style = level == 1 ? ParaStyle.CHAPTER_TITLE : level == 2 ? ParaStyle.HEADING : ParaStyle.SUBHEADING;
                if (level == 1) b.replaceTitle(inlines(h.group(2), notes));
                else b.add(new Block.Paragraph(style, inlines(h.group(2), notes)));
                continue;
            }
            if (SCENE_BREAK.matcher(line).matches()) {
                b.flush(para, paraStyle, notes);
                b.add(new Block.SceneBreak());
                continue;
            }
            Matcher pic = MD_PICTURE.matcher(line.strip());
            if (pic.matches()) {
                b.flush(para, paraStyle, notes);
                String name = picture(pic.group(2), folder, book);
                if (name == null) missing.add(pic.group(2));
                else b.add(new Block.Picture(name, pic.group(1), pic.group(3) == null ? "" : pic.group(3), PictureSize.MEDIUM));
                continue;
            }
            ParaStyle style = ParaStyle.TEXT;
            String content = line;
            if (line.startsWith(">")) {
                style = ParaStyle.QUOTE;
                content = line.replaceFirst("^>\\s?", "");
            }
            if (style != paraStyle && !para.isEmpty()) b.flush(para, paraStyle, notes);
            paraStyle = style;
            if (!para.isEmpty()) para.append(raw.endsWith("  ") ? "\n" : " ");
            para.append(content.strip());
        }
        b.flush(para, paraStyle, notes);
        return new Result(b.finish(), missing);
    }

    /** Adds a picture next to the Markdown file to the book; null when there is none or not of a known type. */
    private static String picture(String href, Path folder, Book book) {
        if (href.matches("(?i)https?://.*") || folder == null) return null;
        try {
            Path file = folder.resolve(java.net.URLDecoder.decode(href, StandardCharsets.UTF_8)).normalize();
            String type = Resource.mediaTypeOf(file.getFileName().toString());
            if (type.isEmpty() || !Files.isRegularFile(file)) return null;
            return book.addResource(file.getFileName().toString(), type, Files.readAllBytes(file));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    static List<Inline> inlines(String text, Map<String, String> notes) {
        List<Inline> out = new ArrayList<>();
        inlines(text, EnumSet.noneOf(Mark.class), "", notes, out);
        return out;
    }

    private static void inlines(String text, Set<Mark> marks, String link, Map<String, String> notes, List<Inline> out) {
        Matcher m = MD_INLINE.matcher(text);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) out.add(new Inline.Text(text.substring(pos, m.start()), marks, link));
            if (m.group(1) != null) {
                out.add(new Inline.Footnote(notes.getOrDefault(m.group(1), "")));
            } else if (m.group(2) != null) {
                inlines(m.group(2), marks, Link.web(m.group(3)) ? m.group(3) : link, notes, out);
            } else if (m.group(4) != null || m.group(5) != null) {
                inlines(m.group(4) != null ? m.group(4) : m.group(5), with(marks, Mark.BOLD), link, notes, out);
            } else if (m.group(6) != null || m.group(7) != null) {
                inlines(m.group(6) != null ? m.group(6) : m.group(7), with(marks, Mark.ITALIC), link, notes, out);
            } else {
                out.add(new Inline.Text(m.group(8), marks, link));
            }
            pos = m.end();
        }
        if (pos < text.length()) out.add(new Inline.Text(text.substring(pos), marks, link));
    }

    private static Set<Mark> with(Set<Mark> marks, Mark mark) {
        Set<Mark> s = EnumSet.noneOf(Mark.class);
        s.addAll(marks);
        s.add(mark);
        return s;
    }

    /** Collects blocks into chapters; text before the first chapter heading becomes a chapter of its own. */
    private static final class Builder {
        private final List<Chapter> chapters = new ArrayList<>();
        private List<Block> current;

        void chapter(String title) {
            close();
            current = new ArrayList<>();
            current.add(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, title));
        }

        void replaceTitle(List<Inline> title) {
            current.set(0, new Block.Paragraph(ParaStyle.CHAPTER_TITLE, title));
        }

        void add(Block block) {
            if (current == null) {
                current = new ArrayList<>();
                current.add(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, ""));
            }
            current.add(block);
        }

        void flush(StringBuilder para, ParaStyle style, Map<String, String> notes) {
            if (para.isEmpty()) return;
            for (String line : para.toString().split("\n")) add(new Block.Paragraph(style, inlines(line, notes)));
            para.setLength(0);
        }

        private void close() {
            if (current != null) chapters.add(new Chapter(Chapter.newId(), true, current));
            current = null;
        }

        List<Chapter> finish() {
            close();
            if (chapters.isEmpty()) chapters.add(Chapter.create(""));
            return chapters;
        }
    }
}
