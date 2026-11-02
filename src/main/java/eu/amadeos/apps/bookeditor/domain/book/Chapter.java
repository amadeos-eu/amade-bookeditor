package eu.amadeos.apps.bookeditor.domain.book;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A chapter (or a foreword, an afterword) of the book: one file in the EPUB.
 *
 * @param id       fixed while the book exists: names the file and is the target of links, so chapters can be
 *                 moved and renamed without breaking links
 * @param numbered counted as "Chapter 1, 2, …"; forewords and afterwords are not
 */
public record Chapter(String id, boolean numbered, List<Block> blocks) {
    public Chapter {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("chapter id: " + id);
        blocks = List.copyOf(blocks);
    }

    /** A new chapter with a title paragraph and an empty text paragraph. */
    public static Chapter create(String title) {
        return new Chapter(newId(), true, List.of(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, title),
                new Block.Paragraph(ParaStyle.TEXT, "")));
    }

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** The text of the first chapter-title paragraph, empty without one. */
    public String title() {
        for (Block b : blocks) {
            if (b instanceof Block.Paragraph p && p.style() == ParaStyle.CHAPTER_TITLE) return p.plainText().strip();
        }
        return "";
    }

    public Chapter withBlocks(List<Block> newBlocks) {
        return new Chapter(id, numbered, newBlocks);
    }

    public Chapter withNumbered(boolean on) {
        return new Chapter(id, on, blocks);
    }

    /** The words of the chapter, footnotes included. */
    public int words() {
        int n = 0;
        for (Block b : blocks) {
            if (b instanceof Block.Paragraph p) {
                for (Inline i : p.content()) {
                    n += Words.count(switch (i) {
                        case Inline.Text t -> t.text();
                        case Inline.Footnote f -> f.text();
                    });
                }
            } else if (b instanceof Block.Picture pic) {
                n += Words.count(pic.caption());
            }
        }
        return n;
    }
}
