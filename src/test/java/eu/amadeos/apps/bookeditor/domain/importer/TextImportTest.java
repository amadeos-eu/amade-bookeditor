package eu.amadeos.apps.bookeditor.domain.importer;

import eu.amadeos.apps.bookeditor.TestBooks;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextImportTest {
    private static Book book() {
        return new Book(BookInfo.create("T", "A", "en"), Look.CLASSIC);
    }

    @Test
    void plainTextIsSplitAtChapterLines() {
        TextImport.Result r = TextImport.plain("""
                Chapter 1: The Harbour

                The lamp was lit.
                It burned all night.

                Chapter 2
                The Storm

                Rain came.
                """);
        assertEquals(2, r.chapters().size());
        assertEquals("The Harbour", r.chapters().get(0).title());
        assertEquals("The Storm", r.chapters().get(1).title());
        Block.Paragraph p = (Block.Paragraph) r.chapters().get(0).blocks().get(1);
        assertEquals("The lamp was lit. It burned all night.", p.plainText());
    }

    @Test
    void germanAndRomanChapterLinesAreFound() {
        TextImport.Result r = TextImport.plain("Kapitel IV. Der Sturm\n\nText.\n\nKapitel V. Danach\n\nMehr.");
        assertEquals(List.of("Der Sturm", "Danach"), r.chapters().stream().map(Chapter::title).toList());
    }

    @Test
    void textBeforeTheFirstChapterLineStaysInAChapterOfItsOwn() {
        TextImport.Result r = TextImport.plain("A foreword.\n\nChapter 1 Start\n\nText.");
        assertEquals(2, r.chapters().size());
        assertTrue(r.chapters().get(0).blocks().stream().anyMatch(b -> b instanceof Block.Paragraph p
                && p.plainText().equals("A foreword.")));
    }

    @Test
    void threeStarsBecomeASceneBreak() {
        TextImport.Result r = TextImport.plain("Chapter 1 A\n\nBefore.\n\n* * *\n\nAfter.");
        List<Block> blocks = r.chapters().getFirst().blocks();
        assertTrue(blocks.stream().anyMatch(b -> b instanceof Block.SceneBreak));
    }

    @Test
    void markdownHeadingsBecomeChaptersAndHeadings() {
        TextImport.Result r = TextImport.markdown("""
                # One

                Text with **bold** and *italic*.

                ## Inside

                > A quotation.

                # Two

                More.
                """, null, book());
        assertEquals(2, r.chapters().size());
        Chapter one = r.chapters().getFirst();
        assertEquals("One", one.title());
        Block.Paragraph text = (Block.Paragraph) one.blocks().get(1);
        assertTrue(text.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("bold")
                && t.marks().equals(Set.of(Mark.BOLD))));
        assertTrue(text.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("italic")
                && t.marks().equals(Set.of(Mark.ITALIC))));
        assertEquals(ParaStyle.HEADING, ((Block.Paragraph) one.blocks().get(2)).style());
        assertEquals(ParaStyle.QUOTE, ((Block.Paragraph) one.blocks().get(3)).style());
    }

    @Test
    void markdownFootnotesAndLinksComeAlong() {
        TextImport.Result r = TextImport.markdown("""
                # One

                See [the site](https://amadeos.eu) and a note[^1].

                [^1]: The note text.
                """, null, book());
        Block.Paragraph p = (Block.Paragraph) r.chapters().getFirst().blocks().get(1);
        assertTrue(p.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.link().equals("https://amadeos.eu")));
        assertTrue(p.content().stream().anyMatch(i -> i instanceof Inline.Footnote f && f.text().equals("The note text.")));
    }

    @Test
    void picturesNextToTheMarkdownFileAreAddedAndMissingOnesReported(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("lamp.png"), TestBooks.png(20, 10, Color.ORANGE));
        Path md = dir.resolve("book.md");
        Files.writeString(md, "# One\n\n![A lamp](lamp.png \"The lamp\")\n\n![Gone](gone.jpg)\n");
        Book b = book();
        TextImport.Result r = TextImport.read(md, b);
        Block.Picture pic = assertInstanceOf(Block.Picture.class, r.chapters().getFirst().blocks().get(1));
        assertEquals("A lamp", pic.description());
        assertEquals("The lamp", pic.caption());
        assertTrue(b.resource(pic.resource()).isPresent());
        assertEquals(List.of("gone.jpg"), r.missingPictures());
    }

    @Test
    void olderFilesInWindows1252AreRead(@TempDir Path dir) throws Exception {
        Path txt = dir.resolve("alt.txt");
        Files.write(txt, "Chapter 1 Café\r\n\r\nÄpfel und Birnen.\r\n".getBytes(Charset.forName("windows-1252")));
        TextImport.Result r = TextImport.read(txt, book());
        assertEquals("Café", r.chapters().getFirst().title());
        assertEquals("Äpfel und Birnen.", ((Block.Paragraph) r.chapters().getFirst().blocks().get(1)).plainText());
    }

    @Test
    void markdownCodeBlocksKeepTheirLines() {
        TextImport.Result r = TextImport.markdown("# One\n\n```\nint a = 1;\n  b();\n```\n", null, book());
        List<Block> blocks = r.chapters().getFirst().blocks();
        assertEquals(ParaStyle.CODE, ((Block.Paragraph) blocks.get(1)).style());
        assertEquals("  b();", ((Block.Paragraph) blocks.get(2)).plainText());
    }
}
