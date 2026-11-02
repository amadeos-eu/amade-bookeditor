package eu.amadeos.apps.bookeditor.domain.epub;

import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.reporting.CheckingReport;
import eu.amadeos.apps.bookeditor.TestBooks;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubRoundTripTest {
    @TempDir
    Path dir;

    static final Instant SAVED = Instant.parse("2026-11-02T19:30:00Z");

    static Path save(Book book, Path file) throws Exception {
        new EpubWriter(book, SAVED, "1.0.0").save(file);
        return file;
    }

    /** Runs the W3C checker and returns its messages; empty when the book has no errors and no warnings. */
    static String check(Path file) {
        StringWriter json = new StringWriter();
        CheckingReport report = new CheckingReport(new PrintWriter(json), file.getFileName().toString());
        new EpubCheck(file.toFile(), report).doValidate();
        if (report.getFatalErrorCount() + report.getErrorCount() + report.getWarningCount() == 0) return "";
        report.generate();
        return json.toString();
    }

    @Test
    void theFullBookPassesEpubCheck() throws Exception {
        Path file = save(TestBooks.full(), dir.resolve("Harbour Light.epub"));
        assertEquals("", check(file));
    }

    @Test
    void aNewBookPassesEpubCheckInEveryLook() throws Exception {
        for (Look look : Look.values()) {
            Book book = Book.create(BookInfo.create("Empty", "", "de"), look, "Erstes Kapitel");
            assertEquals("", check(save(book, dir.resolve(look.key() + ".epub"))), look.name());
        }
    }

    @Test
    void mimetypeIsTheFirstEntryAndStored() throws Exception {
        Path file = save(TestBooks.full(), dir.resolve("b.epub"));
        byte[] head = Files.readAllBytes(file);
        assertEquals("mimetypeapplication/epub+zip", new String(head, 30, 28, StandardCharsets.US_ASCII));
        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertEquals(ZipEntry.STORED, zip.getEntry("mimetype").getMethod());
        }
    }

    @Test
    void chaptersComeBackAsTheyWere() throws Exception {
        Book book = TestBooks.full();
        Book back = EpubReader.read(save(book, dir.resolve("c.epub")));
        assertEquals(book.chapters().size(), back.chapters().size());
        for (int i = 0; i < book.chapters().size(); i++) {
            Chapter expected = book.chapters().get(i);
            assertEquals(expected, back.chapters().get(i), "chapter " + i);
        }
        assertEquals(book.dedication(), back.dedication());
    }

    @Test
    void detailsCoverAndPicturesComeBack() throws Exception {
        Book book = TestBooks.full();
        Book back = EpubReader.read(save(book, dir.resolve("d.epub")));
        assertEquals(book.info(), back.info());
        assertEquals(book.look(), back.look());
        assertEquals(book.cover(), back.cover());
        assertEquals(book.resources(), back.resources());
    }

    @Test
    void savingAgainReplacesTheFile() throws Exception {
        Path file = dir.resolve("e.epub");
        Book book = TestBooks.full();
        save(book, file);
        book.setInfo(book.info().withTitle("Second Title"));
        save(book, file);
        assertEquals("Second Title", EpubReader.read(file).info().title());
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "no temporary file is left");
        }
    }

    @Test
    void booksOfOtherProgramsAreRefused() throws Exception {
        Path file = save(TestBooks.full(), dir.resolve("f.epub"));
        String opf;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            opf = new String(zip.getInputStream(zip.getEntry(EpubWriter.OPF)).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(opf.contains(EpubWriter.EDITOR_PROPERTY));
        Path other = dir.resolve("other.epub");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(other));
             ZipFile zip = new ZipFile(file.toFile())) {
            for (var e : java.util.Collections.list(zip.entries())) {
                out.putNextEntry(new ZipEntry(e.getName()));
                byte[] data = zip.getInputStream(e).readAllBytes();
                if (e.getName().equals(EpubWriter.OPF)) {
                    data = opf.replaceAll("<meta property=\"amadeos:editor\">[^<]*</meta>", "").getBytes(StandardCharsets.UTF_8);
                }
                out.write(data);
            }
        }
        var ex = assertThrows(EpubReader.NotEditableException.class, () -> EpubReader.read(other));
        assertTrue(ex.epub());
        Path text = Files.writeString(dir.resolve("text.epub"), "no zip");
        assertFalse(assertThrows(EpubReader.NotEditableException.class, () -> EpubReader.read(text)).epub());
    }

    @Test
    void theOpfCarriesSeriesRolesAndAccessibility() throws Exception {
        String opf = new EpubWriter(TestBooks.full(), SAVED, "1.0.0").opf();
        assertTrue(opf.contains("<meta refines=\"#series\" property=\"group-position\">1</meta>"));
        assertTrue(opf.contains("<dc:contributor id=\"person-2\">Tom Weiss</dc:contributor>"));
        assertTrue(opf.contains(">ill</meta>"));
        assertTrue(opf.contains("<dc:identifier id=\"isbn\">urn:isbn:9783161484100</dc:identifier>"));
        assertTrue(opf.contains("schema:accessibilityFeature\">alternativeText"));
        assertTrue(opf.contains("<meta property=\"dcterms:modified\">2026-11-02T19:30:00Z</meta>"));
        assertTrue(opf.contains("© 2026 Anna Berger. CC BY 4.0"));
    }

    @Test
    void theChapterNumberIsInTheBookLanguage() {
        Book book = TestBooks.full();
        book.setInfo(new BookInfo(book.info().identifier(), "T", "", book.info().people(), "de", "", 0, "", "", "",
                java.util.List.of(), "", null, ""));
        String xhtml = ChapterWriter.write(book, book.chapters().get(1));
        assertTrue(xhtml.contains("<p class=\"chapter-label\">Kapitel 2</p>"), xhtml);
        assertFalse(ChapterWriter.write(book, book.chapters().get(2)).contains("chapter-label"));
    }
}
