package eu.amadeos.apps.bookeditor.domain.check;

import java.util.Objects;

/**
 * One thing Check Book found.
 *
 * @param chapterId the chapter it is in, empty for the book as a whole
 * @param detail    what to name in the message: a picture, a link text, the checker's own message
 */
public record Finding(Kind kind, String chapterId, String detail) {
    public Finding {
        Objects.requireNonNull(kind, "kind");
        chapterId = chapterId == null ? "" : chapterId;
        detail = detail == null ? "" : detail;
    }

    /** Problems should be fixed before publishing; hints make the book better. */
    public enum Severity {
        PROBLEM, HINT
    }

    /** What the button next to a finding does. */
    public enum Action {
        /** Opens the picture's settings. */
        DESCRIBE,
        /** Shows the place in the chapter. */
        SHOW,
        /** Opens Book Details. */
        DETAILS,
        /** Opens Book Details on the Description tab. */
        DESCRIPTION,
        /** Opens the cover maker. */
        COVER,
        /** Makes big pictures smaller. */
        SHRINK,
        /** Shows the checker's message in full. */
        DETAILS_TEXT,
        NONE
    }

    public enum Kind {
        NO_TITLE(Severity.PROBLEM, Action.DETAILS),
        NO_CHAPTERS(Severity.PROBLEM, Action.NONE),
        PICTURE_WITHOUT_DESCRIPTION(Severity.PROBLEM, Action.DESCRIBE),
        LINK_TO_DELETED_CHAPTER(Severity.PROBLEM, Action.SHOW),
        BAD_WEB_LINK(Severity.PROBLEM, Action.SHOW),
        EMPTY_FOOTNOTE(Severity.PROBLEM, Action.SHOW),
        EMPTY_CHAPTER(Severity.PROBLEM, Action.SHOW),
        BAD_DATE(Severity.PROBLEM, Action.DETAILS),
        BAD_ISBN(Severity.PROBLEM, Action.DETAILS),
        EPUBCHECK_ERROR(Severity.PROBLEM, Action.DETAILS_TEXT),
        NO_AUTHOR(Severity.HINT, Action.DETAILS),
        NO_DESCRIPTION(Severity.HINT, Action.DESCRIPTION),
        NO_COVER(Severity.HINT, Action.COVER),
        CHAPTER_WITHOUT_TITLE(Severity.HINT, Action.SHOW),
        BIG_PICTURE(Severity.HINT, Action.SHRINK),
        EPUBCHECK_WARNING(Severity.HINT, Action.DETAILS_TEXT);

        private final Severity severity;
        private final Action action;

        Kind(Severity severity, Action action) {
            this.severity = severity;
            this.action = action;
        }

        public Severity severity() {
            return severity;
        }

        public Action action() {
            return action;
        }

        /** The name in the texts: {@code check.<key>.title} and {@code check.<key>.text}. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }
}
