package eu.amadeos.apps.bookeditor.domain.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One paragraph-sized piece of a chapter. */
public sealed interface Block {

    /** A paragraph of text in one style. */
    record Paragraph(ParaStyle style, List<Inline> content) implements Block {
        public Paragraph {
            Objects.requireNonNull(style, "style");
            content = merge(content);
        }

        public Paragraph(ParaStyle style, String text) {
            this(style, text.isEmpty() ? List.of() : List.of(new Inline.Text(text)));
        }

        /** The text without formatting; footnotes are left out. */
        public String plainText() {
            StringBuilder sb = new StringBuilder();
            for (Inline i : content) if (i instanceof Inline.Text t) sb.append(t.text());
            return sb.toString();
        }

        public boolean isEmpty() {
            return content.isEmpty();
        }

        /** Joins neighbouring texts of the same format and drops empty ones. */
        private static List<Inline> merge(List<Inline> parts) {
            List<Inline> out = new ArrayList<>();
            for (Inline part : parts) {
                if (part instanceof Inline.Text t) {
                    if (t.text().isEmpty()) continue;
                    if (!out.isEmpty() && out.getLast() instanceof Inline.Text last && last.sameFormat(t)) {
                        out.set(out.size() - 1, new Inline.Text(last.text() + t.text(), last.marks(), last.link()));
                        continue;
                    }
                }
                out.add(part);
            }
            return List.copyOf(out);
        }
    }

    /**
     * A picture on its own line.
     *
     * @param resource    the name of the picture in the book ({@link Book#resources()})
     * @param description what blind readers hear instead of seeing it ("alt text"); empty for decoration only
     * @param caption     the line under the picture, may be empty
     */
    record Picture(String resource, String description, String caption, PictureSize size) implements Block {
        public Picture {
            Objects.requireNonNull(resource, "resource");
            description = description == null ? "" : description.strip();
            caption = caption == null ? "" : caption.strip();
            size = size == null ? PictureSize.MEDIUM : size;
        }
    }

    /** A change of scene, shown as a short line or "* * *". */
    record SceneBreak() implements Block {
    }

    /**
     * Content of a book made elsewhere that the editor cannot edit (a table, a list, a formula, a drawing): kept
     * as it was and written back unchanged. The editor shows it as a grey box; the writer can turn it into plain
     * paragraphs or delete it.
     *
     * @param kind  what it is, for the box's label
     * @param xhtml the content as XHTML; pictures in it point to {@code ../images/<name>}
     * @param text  its text without markup, for counting words and for the preview in the box
     */
    record Kept(KeptKind kind, String xhtml, String text) implements Block {
        private static final java.util.regex.Pattern PICTURE =
                java.util.regex.Pattern.compile("(?:src|xlink:href|href)=\"\\.\\./images/([^\"]+)\"");

        public Kept {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(xhtml, "xhtml");
            text = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        }

        /** The book's pictures the content shows, by resource name. */
        public List<String> pictures() {
            List<String> names = new ArrayList<>();
            java.util.regex.Matcher m = PICTURE.matcher(xhtml);
            while (m.find()) names.add(m.group(1));
            return names;
        }
    }
}
