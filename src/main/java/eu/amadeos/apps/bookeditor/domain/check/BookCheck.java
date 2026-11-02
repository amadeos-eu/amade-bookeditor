package eu.amadeos.apps.bookeditor.domain.check;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Resource;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The editor's own checks: what a reader, a shop or a library would miss, said in plain words. They run on the
 * book in memory, so they are fast enough to run while writing.
 */
public final class BookCheck {
    /** Pictures larger than this make the book slow to open on small e-readers. */
    public static final int BIG_PICTURE_BYTES = 1_500_000;

    private BookCheck() {
    }

    // --8<-- [start:check]
    public static List<Finding> check(Book book) {
        List<Finding> out = new ArrayList<>();
        BookInfo info = book.info();
        if (info.title().isBlank()) out.add(new Finding(Finding.Kind.NO_TITLE, "", ""));
        if (book.chapters().isEmpty()) out.add(new Finding(Finding.Kind.NO_CHAPTERS, "", ""));
        if (!info.published().isEmpty() && !validDate(info.published())) {
            out.add(new Finding(Finding.Kind.BAD_DATE, "", info.published()));
        }
        if (!info.isbn().isEmpty() && !validIsbn(info.isbn())) out.add(new Finding(Finding.Kind.BAD_ISBN, "", info.isbn()));

        Set<String> ids = new HashSet<>();
        for (Chapter c : book.chapters()) ids.add(c.id());
        List<Chapter> all = new ArrayList<>();
        book.dedication().ifPresent(all::add);
        all.addAll(book.chapters());
        for (Chapter c : all) chapter(book, c, ids, out);

        if (info.people().isEmpty()) out.add(new Finding(Finding.Kind.NO_AUTHOR, "", ""));
        if (info.description().isBlank()) out.add(new Finding(Finding.Kind.NO_DESCRIPTION, "", ""));
        if (book.cover().isEmpty()) out.add(new Finding(Finding.Kind.NO_COVER, "", ""));
        for (Map.Entry<String, Resource> r : book.resources().entrySet()) {
            if (r.getValue().size() > BIG_PICTURE_BYTES) out.add(new Finding(Finding.Kind.BIG_PICTURE, "", r.getKey()));
        }
        return out;
    }
    // --8<-- [end:check]

    private static void chapter(Book book, Chapter c, Set<String> ids, List<Finding> out) {
        boolean dedication = c.id().equals(Book.DEDICATION_ID);
        boolean hasText = false;
        for (Block b : c.blocks()) {
            switch (b) {
                case Block.Picture p -> {
                    hasText = true;
                    if (p.description().isEmpty()) {
                        out.add(new Finding(Finding.Kind.PICTURE_WITHOUT_DESCRIPTION, c.id(), p.resource()));
                    }
                }
                case Block.SceneBreak ignored -> {
                }
                case Block.Kept k -> hasText = true;
                case Block.Paragraph p -> {
                    if (!p.plainText().isBlank() && !(p.style() == eu.amadeos.apps.bookeditor.domain.book.ParaStyle.CHAPTER_TITLE)) {
                        hasText = true;
                    }
                    for (Inline i : p.content()) {
                        if (i instanceof Inline.Footnote f && f.text().isEmpty()) {
                            out.add(new Finding(Finding.Kind.EMPTY_FOOTNOTE, c.id(), ""));
                        } else if (i instanceof Inline.Text t && !t.link().isEmpty()) {
                            var target = Link.chapterId(t.link());
                            if (target.isPresent()) {
                                if (!ids.contains(target.get())) {
                                    out.add(new Finding(Finding.Kind.LINK_TO_DELETED_CHAPTER, c.id(), t.text()));
                                }
                            } else if (!validWebLink(t.link())) {
                                out.add(new Finding(Finding.Kind.BAD_WEB_LINK, c.id(), t.link()));
                            }
                        }
                    }
                }
            }
        }
        if (!hasText) out.add(new Finding(Finding.Kind.EMPTY_CHAPTER, c.id(), ""));
        if (!dedication && c.title().isEmpty()) out.add(new Finding(Finding.Kind.CHAPTER_WITHOUT_TITLE, c.id(), ""));
    }

    static boolean validDate(String date) {
        if (date.matches("\\d{4}") || date.matches("\\d{4}-(0[1-9]|1[0-2])")) return true;
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** ISBN-10 or ISBN-13 with a correct check digit; hyphens and spaces are allowed. */
    public static boolean validIsbn(String isbn) {
        String d = isbn.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        if (d.matches("\\d{13}")) {
            int sum = 0;
            for (int i = 0; i < 13; i++) sum += (d.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            return sum % 10 == 0;
        }
        if (d.matches("\\d{9}[\\dX]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) sum += (d.charAt(i) == 'X' ? 10 : d.charAt(i) - '0') * (10 - i);
            return sum % 11 == 0;
        }
        return false;
    }

    public static boolean validWebLink(String link) {
        if (!Link.web(link)) return false;
        try {
            URI uri = new URI(link);
            return link.toLowerCase(java.util.Locale.ROOT).startsWith("mailto:") ? uri.getSchemeSpecificPart().contains("@")
                    : uri.getHost() != null && uri.getHost().contains(".");
        } catch (Exception e) {
            return false;
        }
    }
}
