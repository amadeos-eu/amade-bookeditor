package eu.amadeos.apps.bookeditor.domain.importer;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.KeptKind;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.commons.process.FinishedProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same short manuscript (src/test/resources/…/import/manuscript.md) saved as ODT and DOCX by two different
 * programs must come in the same way.
 */
class OfficeImportTest {
    @TempDir
    Path dir;

    static Path fixture(String name) throws URISyntaxException {
        return Path.of(OfficeImportTest.class.getResource("/eu/amadeos/apps/bookeditor/import/" + name).toURI());
    }

    private static Book book() {
        return new Book(BookInfo.create("The Harbour Light", "Anna Berger", "en"), Look.CLASSIC);
    }

    private static List<Block.Paragraph> paragraphs(Chapter c) {
        return c.blocks().stream().filter(b -> b instanceof Block.Paragraph).map(b -> (Block.Paragraph) b).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pandoc.odt", "libreoffice.odt", "pandoc.docx", "libreoffice.docx"})
    void aDocumentBecomesChaptersWithItsFormatting(String name) throws Exception {
        Book book = book();
        List<Chapter> chapters = BookImport.read(fixture(name), book, Optional::empty).chapters();
        // The document's title block is left out: the editor makes a title page of its own
        assertEquals(List.of("The Storm", "The Morning"), chapters.stream().map(Chapter::title).toList());
        Chapter storm = chapters.get(0);
        Block.Paragraph first = paragraphs(storm).get(1);
        assertEquals("It was a dark and stormy night when the keeper climbed the stairs.", first.plainText());
        assertTrue(first.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("dark") && t.has(Mark.ITALIC)));
        assertTrue(first.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("stormy") && t.has(Mark.BOLD)));
        assertEquals(new Inline.Footnote("The stairs had 211 steps."), first.content().getLast());
        assertTrue(paragraphs(storm).stream().anyMatch(p -> p.style() == ParaStyle.QUOTE
                && p.plainText().equals("The sea does not forgive.")));
        Block.Paragraph water = paragraphs(storm).stream().filter(p -> p.plainText().startsWith("The water")).findFirst().orElseThrow();
        assertTrue(water.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("2") && t.has(Mark.SUBSCRIPT)));
        assertTrue(water.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("3") && t.has(Mark.SUPERSCRIPT)));
        assertTrue(water.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.link().equals("https://example.org/museum")));
        Block.Picture picture = storm.blocks().stream().filter(b -> b instanceof Block.Picture).map(b -> (Block.Picture) b)
                .findFirst().orElseThrow();
        assertEquals("The lighthouse at night", picture.caption());
        assertTrue(book.resource(picture.resource()).isPresent());
        assertTrue(paragraphs(storm).stream().anyMatch(p -> p.style() == ParaStyle.HEADING && p.plainText().equals("On the Rocks")));
        assertTrue(paragraphs(storm).stream().anyMatch(p -> p.plainText().equals("• Lantern")));
        assertTrue(paragraphs(storm).stream().anyMatch(p -> p.plainText().equals("2. Second")));
        Block.Kept table = storm.blocks().stream().filter(b -> b instanceof Block.Kept).map(b -> (Block.Kept) b)
                .findFirst().orElseThrow();
        assertEquals(KeptKind.TABLE, table.kind());
        assertEquals("Day Weather Monday Rain Tuesday Storm", table.text());

        Chapter morning = chapters.get(1);
        assertEquals(new Inline.Footnote("As it always does."), paragraphs(morning).get(1).content().getLast());
        assertTrue(paragraphs(morning).stream().anyMatch(p -> p.style() == ParaStyle.SUBHEADING));
        // Headings carry no marks of their own: the look decides how they appear
        assertTrue(paragraphs(morning).getFirst().content().stream()
                .allMatch(i -> i instanceof Inline.Text t && t.marks().isEmpty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pandoc.odt", "pandoc.docx", "libreoffice.docx"})
    void threeStarsOrAHorizontalLineAreASceneBreak(String name) throws Exception {
        List<Chapter> chapters = BookImport.read(fixture(name), book(), Optional::empty).chapters();
        assertTrue(chapters.getFirst().blocks().stream().anyMatch(b -> b instanceof Block.SceneBreak), name);
    }

    @Test
    void rtfAndDocGoThroughLibreOffice() throws Exception {
        Path rtf = Files.writeString(dir.resolve("story.rtf"), "{\\rtf1 Hello}");
        AtomicReference<List<String>> ran = new AtomicReference<>();
        LibreOfficeConverter office = new LibreOfficeConverter(Path.of("/usr/bin/soffice"), command -> {
            ran.set(command);
            Path out = Path.of(command.get(command.indexOf("--outdir") + 1));
            try {
                Files.copy(fixture("pandoc.odt"), out.resolve("document.odt"));
            } catch (URISyntaxException e) {
                throw new java.io.IOException(e);
            }
            return FinishedProcess.printing("");
        });
        List<Chapter> chapters = BookImport.read(rtf, book(), () -> Optional.of(office)).chapters();
        assertEquals(List.of("The Storm", "The Morning"), chapters.stream().map(Chapter::title).toList());
        assertTrue(ran.get().contains("--convert-to") && ran.get().contains("odt"), ran.get().toString());
        assertTrue(ran.get().getLast().endsWith("document.rtf"), ran.get().toString());
    }

    @Test
    void withoutLibreOfficeAnRtfFileIsRefusedPolitely() throws Exception {
        Path doc = Files.writeString(dir.resolve("story.doc"), "x");
        assertThrows(BookImport.NeedsLibreOfficeException.class, () -> BookImport.read(doc, book(), Optional::empty));
    }

    @Test
    void aBrokenDocumentIsAnError() throws Exception {
        Path odt = Files.writeString(dir.resolve("story.odt"), "not a zip");
        assertThrows(java.io.IOException.class, () -> BookImport.read(odt, book(), Optional::empty));
    }

    @Test
    void textFilesStillGoThroughTheTextImport() throws Exception {
        Path md = Files.writeString(dir.resolve("story.md"), "# One\n\nText.\n");
        assertEquals(List.of("One"), BookImport.read(md, book(), Optional::empty).chapters().stream().map(Chapter::title).toList());
    }
}
