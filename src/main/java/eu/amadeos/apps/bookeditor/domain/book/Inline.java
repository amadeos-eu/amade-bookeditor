package eu.amadeos.apps.bookeditor.domain.book;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** A piece of a paragraph: text with its marks and perhaps a link, or a footnote. */
public sealed interface Inline {

    /**
     * Text with the same formatting.
     *
     * @param link empty, a web address ({@code https://…}, {@code mailto:…}) or {@link Link#chapter(String)}
     */
    record Text(String text, Set<Mark> marks, String link) implements Inline {
        public Text {
            Objects.requireNonNull(text, "text");
            marks = marks.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(marks));
            link = link == null ? "" : link;
        }

        public Text(String text) {
            this(text, Set.of(), "");
        }

        public boolean has(Mark mark) {
            return marks.contains(mark);
        }

        public boolean sameFormat(Text other) {
            return marks.equals(other.marks) && link.equals(other.link);
        }
    }

    /** A footnote: a number in the text; its text shows up as a pop-up in readers. */
    record Footnote(String text) implements Inline {
        public Footnote {
            text = text == null ? "" : text.strip();
        }
    }
}
