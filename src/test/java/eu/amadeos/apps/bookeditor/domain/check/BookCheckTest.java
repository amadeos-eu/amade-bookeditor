package eu.amadeos.apps.bookeditor.domain.check;

import eu.amadeos.apps.bookeditor.TestBooks;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookCheckTest {
    private static List<Finding.Kind> kinds(Book book) {
        return BookCheck.check(book).stream().map(Finding::kind).toList();
    }

    @Test
    void theFullTestBookHasNoProblems() {
        List<Finding> findings = BookCheck.check(TestBooks.full());
        assertTrue(findings.stream().noneMatch(f -> f.kind().severity() == Finding.Severity.PROBLEM), findings.toString());
    }

    @Test
    void aNewEmptyBookIsMissingTitleTextAndDetails() {
        Book b = Book.create(BookInfo.create("", "", "en"), Look.CLASSIC, "One");
        List<Finding.Kind> k = kinds(b);
        assertTrue(k.containsAll(List.of(Finding.Kind.NO_TITLE, Finding.Kind.EMPTY_CHAPTER, Finding.Kind.NO_AUTHOR,
                Finding.Kind.NO_DESCRIPTION, Finding.Kind.NO_COVER)), k.toString());
    }

    @Test
    void picturesNeedADescription() {
        Book b = TestBooks.full();
        String name = b.addResource("x.png", "image/png", TestBooks.png(10, 10, Color.RED));
        Chapter c = b.chapters().getFirst();
        List<Block> blocks = new java.util.ArrayList<>(c.blocks());
        blocks.add(new Block.Picture(name, "", "", PictureSize.SMALL));
        b.replace(c.withBlocks(blocks));
        Finding f = BookCheck.check(b).stream().filter(x -> x.kind() == Finding.Kind.PICTURE_WITHOUT_DESCRIPTION)
                .findFirst().orElseThrow();
        assertEquals(c.id(), f.chapterId());
        assertEquals(name, f.detail());
        assertEquals(Finding.Action.DESCRIBE, f.kind().action());
    }

    @Test
    void linksToDeletedChaptersAndBrokenWebAddressesAreFound() {
        Book b = TestBooks.full();
        Chapter c = b.chapters().getFirst();
        List<Block> blocks = new java.util.ArrayList<>(c.blocks());
        blocks.add(new Block.Paragraph(ParaStyle.TEXT, List.of(new Inline.Text("gone", Set.of(), Link.chapter("nothere")),
                new Inline.Text(" and ", Set.of(), ""), new Inline.Text("bad", Set.of(), "https://"))));
        b.replace(c.withBlocks(blocks));
        List<Finding.Kind> k = kinds(b);
        assertTrue(k.contains(Finding.Kind.LINK_TO_DELETED_CHAPTER));
        assertTrue(k.contains(Finding.Kind.BAD_WEB_LINK));
    }

    @Test
    void emptyFootnotesAndUntitledChaptersAreFound() {
        Book b = TestBooks.full();
        List<Chapter> list = new java.util.ArrayList<>(b.chapters());
        list.add(new Chapter("untitled", true, List.of(new Block.Paragraph(ParaStyle.TEXT,
                List.of(new Inline.Text("Text", Set.of(), ""), new Inline.Footnote(""))))));
        b.setChapters(list);
        List<Finding> f = BookCheck.check(b);
        assertTrue(f.contains(new Finding(Finding.Kind.EMPTY_FOOTNOTE, "untitled", "")));
        assertTrue(f.contains(new Finding(Finding.Kind.CHAPTER_WITHOUT_TITLE, "untitled", "")));
    }

    @Test
    void datesAndIsbnsAreChecked() {
        assertTrue(BookCheck.validIsbn("978-3-16-148410-0"));
        assertTrue(BookCheck.validIsbn("0-306-40615-2"));
        assertFalse(BookCheck.validIsbn("978-3-16-148410-1"));
        assertTrue(BookCheck.validDate("2026"));
        assertTrue(BookCheck.validDate("2026-11-02"));
        assertFalse(BookCheck.validDate("2. November"));
    }

    @Test
    void webAddressesAreChecked() {
        assertTrue(BookCheck.validWebLink("https://amadeos.eu/docs"));
        assertTrue(BookCheck.validWebLink("mailto:ralf@example.org"));
        assertFalse(BookCheck.validWebLink("https://"));
        assertFalse(BookCheck.validWebLink("amadeos"));
    }

    @Test
    void veryLargePicturesAreAHint() {
        Book b = TestBooks.full();
        b.putResource(new eu.amadeos.apps.bookeditor.domain.book.Resource("huge.jpg", "image/jpeg",
                new byte[BookCheck.BIG_PICTURE_BYTES + 1]));
        Finding f = BookCheck.check(b).stream().filter(x -> x.kind() == Finding.Kind.BIG_PICTURE).findFirst().orElseThrow();
        assertEquals("huge.jpg", f.detail());
        assertEquals(Finding.Severity.HINT, f.kind().severity());
    }

    @Test
    void everyKindHasAMessageKey() {
        for (Finding.Kind k : Finding.Kind.values()) assertTrue(k.key().matches("[a-z]+(-[a-z]+)*"), k.key());
    }

    @Test
    void epubCheckReportsBecomeFindings() throws Exception {
        String json = """
                some log line
                {"checker": {"checkerVersion": "4.2.6"},
                 "messages": [
                   {"ID": "RSC-005", "severity": "ERROR", "message": "Error while parsing",
                    "locations": [{"path": "EPUB/text/chapter-a.xhtml", "line": 12}]},
                   {"ID": "ACC-001", "severity": "WARNING", "message": "Hint", "locations": []},
                   {"ID": "INF-001", "severity": "INFO", "message": "Info"}]}
                """;
        EpubCheckRunner.Result r = EpubCheckRunner.parse(json);
        assertEquals("4.2.6", r.version());
        assertEquals(2, r.findings().size());
        assertEquals(Finding.Kind.EPUBCHECK_ERROR, r.findings().get(0).kind());
        assertEquals("RSC-005: Error while parsing (EPUB/text/chapter-a.xhtml:12)", r.findings().get(0).detail());
        assertEquals(Finding.Kind.EPUBCHECK_WARNING, r.findings().get(1).kind());
    }
}
