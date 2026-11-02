package eu.amadeos.apps.bookeditor.domain.book;

import java.util.Optional;

/** Link targets: web addresses, or a chapter of the same book by its fixed id, so moving chapters keeps links. */
public final class Link {
    private static final String CHAPTER = "chapter:";

    private Link() {
    }

    public static String chapter(String chapterId) {
        return CHAPTER + chapterId;
    }

    /** The chapter id of a link inside the book. */
    public static Optional<String> chapterId(String link) {
        return link != null && link.startsWith(CHAPTER) ? Optional.of(link.substring(CHAPTER.length())) : Optional.empty();
    }

    public static boolean web(String link) {
        if (link == null) return false;
        String l = link.toLowerCase(java.util.Locale.ROOT);
        return l.startsWith("https://") || l.startsWith("http://") || l.startsWith("mailto:");
    }
}
