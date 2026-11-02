package eu.amadeos.apps.bookeditor.domain.epub;

import eu.amadeos.apps.bookeditor.ForeignBooks;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.KeptKind;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignEpubReaderTest {
    @TempDir
    Path dir;

    private static List<Block.Paragraph> paragraphs(Chapter c) {
        return c.blocks().stream().filter(b -> b instanceof Block.Paragraph).map(b -> (Block.Paragraph) b).toList();
    }

    private static List<Inline.Footnote> footnotes(Chapter c) {
        return paragraphs(c).stream().flatMap(p -> p.content().stream())
                .filter(i -> i instanceof Inline.Footnote).map(i -> (Inline.Footnote) i).toList();
    }

    private static <T extends Block> List<T> blocks(Chapter c, Class<T> type) {
        return c.blocks().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void anEpub3ComesOverWithItsChaptersAndWithoutItsOwnFrontPages() throws Exception {
        ForeignEpubReader.Result r = ForeignEpubReader.read(ForeignBooks.epub3(dir.resolve("keeper.epub")));
        Book book = r.book();
        assertEquals("The Lighthouse Keeper", book.info().title());
        assertEquals("Mara Lind", book.info().authorLine());
        assertEquals("urn:uuid:11111111-2222-3333-4444-555555555555", book.info().identifier());
        assertEquals(Look.CLASSIC, book.look());
        assertEquals("cover.png", book.cover().orElseThrow());
        // cover page, title page and contents are left out, the dedication is the book's dedication
        assertEquals(List.of("I: The Storm", "II: The Morning"), book.chapters().stream().map(Chapter::title).toList());
        assertEquals("For Jonas.", paragraphs(book.dedication().orElseThrow()).getFirst().plainText());
        assertTrue(book.chapters().stream().noneMatch(Chapter::numbered), "counting stays off, the titles carry the numbers");
    }

    @Test
    void textFormattingNotesPicturesAndLinksComeAlong() throws Exception {
        ForeignEpubReader.Result r = ForeignEpubReader.read(ForeignBooks.epub3(dir.resolve("keeper.epub")));
        Book book = r.book();
        Chapter one = book.chapters().get(0);
        Chapter two = book.chapters().get(1);
        Block.Paragraph first = paragraphs(one).get(1);
        assertEquals("It was a dark and stormy night.", first.plainText());
        assertTrue(first.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("dark") && t.has(Mark.ITALIC)));
        assertTrue(first.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("stormy") && t.has(Mark.BOLD)),
                "bold set through the style sheet");
        assertEquals(List.of("The stairs had 211 steps."), footnotes(one).stream().map(Inline.Footnote::text).toList());
        assertEquals(1, blocks(one, Block.SceneBreak.class).size());
        assertTrue(paragraphs(one).stream().anyMatch(p -> p.style() == ParaStyle.CENTERED
                && p.plainText().equals("The End of the Beginning")));
        assertTrue(paragraphs(one).stream().anyMatch(p -> p.style() == ParaStyle.QUOTE));
        Block.Picture lamp = blocks(one, Block.Picture.class).getFirst();
        assertEquals("An oil lamp", lamp.description());
        assertEquals("The old lamp", lamp.caption());
        assertTrue(book.resource(lamp.resource()).isPresent());
        Block.Paragraph links = paragraphs(one).stream().filter(p -> p.plainText().startsWith("Read on")).findFirst().orElseThrow();
        assertTrue(links.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.link().equals(Link.chapter(two.id()))),
                "a link to another file points to its chapter");
        assertTrue(links.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.link().equals("https://example.org/")));
        assertTrue(paragraphs(two).stream().anyMatch(p -> p.style() == ParaStyle.HEADING && p.plainText().equals("On the Rocks")));
    }

    @Test
    void tablesListsAndFormulasAreKeptAndVideosLeftOut() throws Exception {
        ForeignEpubReader.Result r = ForeignEpubReader.read(ForeignBooks.epub3(dir.resolve("keeper.epub")));
        Chapter one = r.book().chapters().getFirst();
        List<Block.Kept> kept = blocks(one, Block.Kept.class);
        assertEquals(List.of(KeptKind.TABLE, KeptKind.LIST, KeptKind.FORMULA), kept.stream().map(Block.Kept::kind).toList());
        assertEquals(3, r.kept());
        assertEquals(1, r.dropped(), "the video");
        assertTrue(kept.getFirst().xhtml().startsWith("<table>"), kept.getFirst().xhtml());
        assertEquals("Day Weather Monday Rain", kept.getFirst().text());
        assertTrue(kept.get(2).xhtml().contains("MathML"), kept.get(2).xhtml());
    }

    @Test
    void theTakenOverBookIsSavedAsAValidEpubAndOpensAgainUnchanged() throws Exception {
        Book book = ForeignEpubReader.read(ForeignBooks.epub3(dir.resolve("keeper.epub"))).book();
        Path saved = EpubRoundTripTest.save(book, dir.resolve("keeper (edited).epub"));
        assertEquals("", EpubRoundTripTest.check(saved));
        Book back = EpubReader.read(saved);
        assertEquals(book.chapters(), back.chapters());
        assertEquals(book.dedication(), back.dedication());
    }

    @Test
    void anEpub2WithEndnotesSplitFilesAndClassFormattingComesOver() throws Exception {
        ForeignEpubReader.Result r = ForeignEpubReader.read(ForeignBooks.epub2(dir.resolve("harbour.epub")));
        Book book = r.book();
        assertEquals("Old Harbour", book.info().title());
        assertEquals(List.of(Role.AUTHOR, Role.ILLUSTRATOR), book.info().people().stream().map(p -> p.role()).toList());
        assertEquals("cover.png", book.cover().orElseThrow());
        // The book title in h1 stays as centred text; chapters start at h2; the split file joins its chapter;
        // the notes file disappears into the footnotes
        assertEquals(List.of("", "Erstes Kapitel", "Zweites Kapitel"), book.chapters().stream().map(Chapter::title).toList());
        Chapter first = book.chapters().get(1);
        assertEquals(List.of("Es war fast Mitternacht."), footnotes(first).stream().map(Inline.Footnote::text).toList());
        assertEquals("Noch am selben Abend ging er zum Hafen. Siehe später.", paragraphs(first).getLast().plainText());
        Block.Paragraph p = paragraphs(first).get(1);
        assertTrue(p.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("spät") && t.has(Mark.BOLD)));
        assertTrue(p.content().stream().anyMatch(i -> i instanceof Inline.Text t && t.text().equals("müde") && t.has(Mark.ITALIC)));
        Chapter second = book.chapters().get(2);
        assertEquals(List.of("Über dem Wasser."), footnotes(second).stream().map(Inline.Footnote::text).toList());
        assertEquals(1, blocks(second, Block.SceneBreak.class).size());
        assertTrue(paragraphs(first).getLast().content().stream()
                .anyMatch(i -> i instanceof Inline.Text t && t.link().equals(Link.chapter(second.id()))));
        assertEquals("", EpubRoundTripTest.check(EpubRoundTripTest.save(book, dir.resolve("harbour (edited).epub"))));
    }

    @Test
    void aFileThatIsNoEpubIsRefused() throws Exception {
        Path text = java.nio.file.Files.writeString(dir.resolve("story.epub"), "not a zip");
        assertThrows(java.io.IOException.class, () -> ForeignEpubReader.read(text));
    }

    @Test
    void aBookProtectedAgainstCopyingIsRefusedButObfuscatedFontsAreFine() throws Exception {
        Path drm = ForeignBooks.epub3(dir.resolve("drm.epub"), """
                <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/text/one.xhtml"/></enc:CipherData></enc:EncryptedData>
                </encryption>""");
        assertThrows(ForeignEpubReader.ProtectedException.class, () -> ForeignEpubReader.read(drm));
        Path fonts = ForeignBooks.epub3(dir.resolve("fonts.epub"), """
                <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/fonts/a.otf"/></enc:CipherData></enc:EncryptedData>
                </encryption>""");
        assertEquals(2, ForeignEpubReader.read(fonts).book().chapters().size());
    }

    @Test
    void paths() {
        assertEquals("OEBPS/img/a.png", ForeignEpubReader.resolve("OEBPS/text/one.xhtml", "../img/a.png"));
        assertEquals("OEBPS/text/two.xhtml#x", ForeignEpubReader.resolve("OEBPS/text/one.xhtml", "two.xhtml#x"));
        assertEquals("OEBPS/text/one.xhtml#n1", ForeignEpubReader.resolve("OEBPS/text/one.xhtml", "#n1"));
        assertEquals("a b.png", ForeignEpubReader.resolve("x.xhtml", "a%20b.png"));
    }

    @Test
    void keptContentTurnsIntoParagraphs() {
        Block.Kept table = new Block.Kept(KeptKind.TABLE,
                "<table><tr><th>Day</th><th>Weather</th></tr><tr><td>Monday</td><td>Rain</td></tr></table>", "");
        assertEquals(List.of("Day – Weather", "Monday – Rain"), texts(KeptText.paragraphs(table)));
        Block.Kept list = new Block.Kept(KeptKind.LIST, "<ol><li>Rope</li><li>Lantern<ul><li>oil</li></ul></li></ol>", "");
        assertEquals(List.of("1. Rope", "2. Lantern", "• oil"), texts(KeptText.paragraphs(list)));
    }

    private static List<String> texts(List<Block> blocks) {
        return blocks.stream().map(b -> ((Block.Paragraph) b).plainText()).toList();
    }

    @Test
    void picturesInKeptContentStayInTheBook() {
        Block.Kept k = new Block.Kept(KeptKind.TABLE, "<table><tr><td><img src=\"../images/a.png\" alt=\"\"/></td></tr></table>", "");
        assertEquals(List.of("a.png"), k.pictures());
    }
}
