package eu.amadeos.apps.bookeditor;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.License;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.Person;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Role;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

/** A book with every kind of content the editor offers, for the tests. */
public final class TestBooks {
    private TestBooks() {
    }

    public static byte[] png(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static Block.Paragraph p(ParaStyle style, String text) {
        return new Block.Paragraph(style, text);
    }

    public static Block.Paragraph text(String text) {
        return p(ParaStyle.TEXT, text);
    }

    public static BookInfo info() {
        return new BookInfo("urn:uuid:4f8c2b1e-1d2c-4e5f-9a7b-0c1d2e3f4a5b", "Harbour Light", "A Novel",
                List.of(new Person("Anna Berger", Role.AUTHOR), new Person("Tom Weiss", Role.ILLUSTRATOR)), "en",
                "North Sea Stories", 1, "self-published", "2026-11-02", "978-3-16-148410-0",
                List.of("lighthouse", "North Sea"), "A keeper, a storm & a ship.", License.CC_BY, "");
    }

    /** Two chapters and an afterword, a dedication, a cover and one picture. */
    public static Book full() {
        Book book = new Book(info(), Look.CLASSIC);
        String cover = book.addResource("cover.png", "image/png", png(60, 90, Color.BLUE));
        book.setCover(cover);
        String picture = book.addResource("Leuchtturm Nordmole.png", "image/png", png(80, 40, Color.GRAY));
        book.setDedication(new Chapter(Book.DEDICATION_ID, false, List.of(p(ParaStyle.CENTERED, "For my father"))));
        Chapter one = new Chapter("aaaa1111", true, List.of(
                p(ParaStyle.CHAPTER_TITLE, "The Keeper"),
                p(ParaStyle.EPIGRAPH, "The sea never once asked for permission."),
                text("The barometer had been falling since noon."),
                new Block.Paragraph(ParaStyle.TEXT, List.of(new Inline.Text("He wrote it in the "),
                        new Inline.Text("logbook", Set.of(Mark.ITALIC), ""), new Inline.Text(" with care"),
                        new Inline.Footnote("A logbook is the diary of a ship or a lighthouse."),
                        new Inline.Text(". See "), new Inline.Text("the storm", Set.of(), Link.chapter("bbbb2222")),
                        new Inline.Text(" and "), new Inline.Text("the web", Set.of(Mark.BOLD), "https://amadeos.eu/"),
                        new Inline.Text("."))),
                p(ParaStyle.HEADING, "The Tower"),
                text("A hundred and twelve steps."),
                new Block.Picture(picture, "A white lighthouse at night", "The lighthouse at Nordmole", PictureSize.MEDIUM),
                text("H"),
                new Block.SceneBreak(),
                p(ParaStyle.SUBHEADING, "Night"),
                p(ParaStyle.QUOTE, "Forty years, he said."),
                p(ParaStyle.QUOTE, "And never once."),
                p(ParaStyle.POEM, "The sea is calm to-night,"),
                p(ParaStyle.POEM, "The tide is full"),
                p(ParaStyle.POEM, ""),
                p(ParaStyle.POEM, "Second stanza"),
                p(ParaStyle.LETTER, "Dear Jonas,"),
                p(ParaStyle.LETTER, "come home."),
                p(ParaStyle.CODE, "N 53° 33′"),
                p(ParaStyle.CODE, "E 8° 34′"),
                new Block.Paragraph(ParaStyle.TEXT, List.of(new Inline.Text("H"),
                        new Inline.Text("2", Set.of(Mark.SUBSCRIPT), ""), new Inline.Text("O and 10"),
                        new Inline.Text("3", Set.of(Mark.SUPERSCRIPT), ""),
                        new Inline.Footnote("Second note & <more>.")))));
        Chapter two = new Chapter("bbbb2222", true, List.of(p(ParaStyle.CHAPTER_TITLE, "The Night of the Storm"),
                text("The gulls had gone inland.")));
        Chapter after = new Chapter("cccc3333", false, List.of(p(ParaStyle.CHAPTER_TITLE, "Afterword"),
                text("Thanks."), p(ParaStyle.CENTERED, "The End")));
        book.setChapters(List.of(one, two, after));
        return book;
    }
}
