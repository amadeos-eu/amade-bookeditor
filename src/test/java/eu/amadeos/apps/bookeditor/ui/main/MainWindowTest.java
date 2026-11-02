package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.apps.bookeditor.TestBooks;
import eu.amadeos.apps.bookeditor.bootstrap.BookEditorModule;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.check.EpubCheckRunner;
import eu.amadeos.apps.bookeditor.domain.check.Finding;
import eu.amadeos.apps.bookeditor.domain.epub.EpubReader;
import eu.amadeos.apps.bookeditor.domain.epub.EpubWriter;
import eu.amadeos.apps.bookeditor.repository.Backups;
import eu.amadeos.apps.bookeditor.repository.EditorPreferences;
import eu.amadeos.apps.bookeditor.ui.dialogs.CheckDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.LinkDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.NewBookDialog;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.FinishedProcess;
import eu.amadeos.commons.typeset.Hyphenators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainWindowTest {
    @TempDir
    Path dir;
    private MainWindow window;
    private FakeAsk ask;
    private ExecutorService background;
    private boolean exited;

    interface EdtWork<T> {
        T run() throws Exception;
    }

    static <T> T edt(EdtWork<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.run());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) throw error.get();
        return result.get();
    }

    static void edtRun(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        background = Executors.newSingleThreadExecutor();
        Path jar = Files.writeString(dir.resolve("epubcheck.jar"), "");
        String report = """
                {"checker": {"checkerVersion": "9.9.9"}, "messages": [
                  {"ID": "OPF-085", "severity": "WARNING", "message": "A warning", "locations": []}]}
                """;
        EpubCheckRunner epubcheck = new EpubCheckRunner(command -> FinishedProcess.printing(report), jar);
        ask = new FakeAsk();
        window = edt(() -> {
            MainWindow w = new MainWindow(Messages.load(BookEditorModule.class, BookEditorModule.MESSAGES, Locale.ENGLISH),
                    null, new EditorPreferences(dir.resolve("prefs.properties")), new Backups(dir.resolve("backups")),
                    epubcheck, new Hyphenators(dir.resolve("no-hyphen")), new DesktopLauncher(), background, null);
            w.setAsk(ask);
            w.setExitAction(() -> exited = true);
            w.setClock(() -> Instant.parse("2026-11-02T18:00:00Z"));
            return w;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (window != null) edtRun(window::dispose);
        if (background != null) background.shutdownNow();
    }

    private void newBook(String title) throws Exception {
        ask.newBook = new NewBookDialog.Choice(title, "Anna Berg", "en", Look.CLASSIC, Optional.empty());
        edtRun(window::newBook);
    }

    private void type(String text) throws Exception {
        edtRun(() -> window.editor().replaceSelection(text));
    }

    private Chapter current() throws Exception {
        return edt(() -> {
            window.commit();
            return window.book().chapter(window.currentChapterId()).orElseThrow();
        });
    }

    @Test
    void theWelcomePageComesFirstAndNothingCanBeEdited() throws Exception {
        assertNull(window.book());
        assertNull(window.currentChapterId());
        assertFalse(edt(() -> window.actionFor("menu.book.save").isEnabled()));
        assertTrue(edt(() -> window.actionFor("menu.book.new").isEnabled()));
    }

    @Test
    void aNewBookOpensItsFirstChapterAndCountsWords() throws Exception {
        newBook("Harbour Light");
        assertEquals("Harbour Light", window.book().info().title());
        assertEquals("Anna Berg", window.book().info().authorLine());
        assertNotNull(window.currentChapterId());
        assertTrue(window.dirty());
        edtRun(() -> window.editor().setCaretPosition(window.editor().getDocument().getLength()));
        type("The lamp was lit at dusk.");
        Chapter c = current();
        assertTrue(c.blocks().stream().anyMatch(b -> b instanceof Block.Paragraph p
                && p.plainText().equals("The lamp was lit at dusk.")));
        edtRun(() -> {
            window.commit();
        });
        assertTrue(window.getTitle().startsWith("*Harbour Light"));
    }

    @Test
    void savingAsksWhereOnceAndWritesAValidBook() throws Exception {
        newBook("Harbour Light");
        edtRun(() -> window.editor().setCaretPosition(window.editor().getDocument().getLength()));
        type("Text of the first chapter.");
        ask.saveAs = dir.resolve("Harbour");
        assertTrue(edt(window::save));
        Path file = dir.resolve("Harbour.epub");
        assertEquals(file, window.file());
        assertFalse(window.dirty());
        assertTrue(ask.asked.contains("save-as Harbour Light.epub"));
        Book back = EpubReader.read(file);
        assertEquals("Harbour Light", back.info().title());
        // saving again goes to the same file and keeps the old version
        type(" More.");
        assertTrue(edt(window::save));
        assertEquals(1, ask.asked.stream().filter(a -> a.startsWith("save-as")).count());
        try (var s = Files.list(dir.resolve("backups"))) {
            assertEquals(1, s.count());
        }
    }

    @Test
    void openingABookShowsItsChaptersInTheOutline() throws Exception {
        Path file = dir.resolve("full.epub");
        new EpubWriter(TestBooks.full(), Instant.now(), "test").save(file);
        ask.open = file;
        edtRun(window::open);
        assertEquals(file, window.file());
        assertFalse(window.dirty());
        Book book = window.book();
        int lines = edt(() -> window.outline().outlineList().getModel().getSize());
        // cover, title page, dedication?, contents, separator, chapters, separator, pictures, details
        int expected = 7 + book.chapters().size() + (book.dedication().isPresent() ? 1 : 0);
        assertEquals(expected, lines);
        assertEquals(book.chapters().getFirst().id(), window.currentChapterId());
    }

    @Test
    void foreignEpubsAreOfferedToBooks() throws Exception {
        Path file = dir.resolve("foreign.epub");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("mimetype"));
            zip.write("application/epub+zip".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("META-INF/container.xml"));
            zip.write(("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                    + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                    + "</rootfiles></container>").getBytes());
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("OEBPS/content.opf"));
            zip.write(("<?xml version=\"1.0\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\">"
                    + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>X</dc:title></metadata>"
                    + "<manifest/><spine/></package>").getBytes());
            zip.closeEntry();
        }
        ask.open = file;
        edtRun(window::open);
        assertNull(window.book());
        assertTrue(ask.asked.contains("elsewhere"), ask.asked.toString());
    }

    @Test
    void unsavedChangesAreAskedAboutAndCancelKeepsTheBook() throws Exception {
        newBook("One");
        ask.unsaved = Ask.Unsaved.CANCEL;
        ask.newBook = new NewBookDialog.Choice("Two", "", "en", Look.MODERN, Optional.empty());
        edtRun(window::newBook);
        assertEquals("One", window.book().info().title());
        ask.unsaved = Ask.Unsaved.DISCARD;
        edtRun(window::newBook);
        assertEquals("Two", window.book().info().title());
        assertEquals(Look.MODERN, window.book().look());
    }

    @Test
    void exitAsksAboutChangesThenEnds() throws Exception {
        newBook("One");
        ask.unsaved = Ask.Unsaved.CANCEL;
        edtRun(window::exit);
        assertFalse(exited);
        ask.unsaved = Ask.Unsaved.DISCARD;
        edtRun(window::exit);
        assertTrue(exited);
    }

    @Test
    void aBookCanStartFromAMarkdownFile() throws Exception {
        Path md = Files.writeString(dir.resolve("story.md"), "# First\n\nOne.\n\n# Second\n\nTwo.\n");
        ask.newBook = new NewBookDialog.Choice("Story", "A", "en", Look.CLASSIC, Optional.of(md));
        edtRun(window::newBook);
        assertEquals(List.of("First", "Second"), window.book().chapters().stream().map(Chapter::title).toList());
    }

    @Test
    void chaptersAreAddedMovedAndDeleted() throws Exception {
        newBook("Book");
        String first = window.currentChapterId();
        edtRun(window::addChapter);
        String second = window.currentChapterId();
        assertEquals(2, window.book().chapters().size());
        assertEquals(second, window.book().chapters().get(1).id());
        edtRun(() -> window.moveChapter(-1));
        assertEquals(second, window.book().chapters().get(0).id());
        assertEquals(first, window.book().chapters().get(1).id());
        edtRun(window::deleteChapter);
        assertEquals(List.of(first), window.book().chapters().stream().map(Chapter::id).toList());
        edtRun(window::deleteChapter);
        assertEquals(1, window.book().chapters().size());
        assertEquals(1, ask.infos.size(), "the last chapter cannot be deleted");
    }

    @Test
    void aChapterIsSplitAtTheCursorAndJoinedAgain() throws Exception {
        newBook("Book");
        edtRun(() -> window.editor().setCaretPosition(window.editor().getDocument().getLength()));
        type("First part.\nThe Second\nSecond part.");
        String id = window.currentChapterId();
        edtRun(() -> {
            String text;
            try {
                text = window.editor().getDocument().getText(0, window.editor().getDocument().getLength());
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
            window.editor().setCaretPosition(text.indexOf("The Second") + 2);
            window.splitChapter();
        });
        assertEquals(2, window.book().chapters().size());
        Chapter second = window.book().chapters().get(1);
        assertEquals("The Second", second.title());
        assertEquals(second.id(), window.currentChapterId());
        assertTrue(window.book().chapter(id).orElseThrow().blocks().stream()
                .noneMatch(b -> b instanceof Block.Paragraph p && p.plainText().contains("Second part")));
        edtRun(() -> window.outline().select(OutlinePanel.Kind.CHAPTER, id));
        edtRun(window::joinWithNext);
        assertEquals(1, window.book().chapters().size());
        Chapter joined = window.book().chapters().getFirst();
        assertTrue(joined.blocks().stream().anyMatch(b -> b instanceof Block.Paragraph p
                && p.style() == ParaStyle.HEADING && p.plainText().equals("The Second")));
    }

    @Test
    void footnotesLinksAndSceneBreaksGoIntoTheChapter() throws Exception {
        newBook("Book");
        edtRun(window::addChapter);
        String target = window.currentChapterId();
        edtRun(() -> window.outline().select(OutlinePanel.Kind.CHAPTER, window.book().chapters().getFirst().id()));
        edtRun(() -> window.editor().setCaretPosition(window.editor().getDocument().getLength()));
        type("See there");
        ask.footnote = "A note.";
        edtRun(() -> window.actionFor("menu.insert.footnote").actionPerformed(null));
        assertEquals(1, ask.footnoteNumber);
        int end = edt(() -> window.editor().getDocument().getLength());
        edtRun(() -> window.editor().select(end - "See there".length() - 1, end - 1 - " there".length()));
        ask.link = new LinkDialog.Answer(Link.chapter(target), "See");
        edtRun(() -> window.actionFor("menu.insert.link").actionPerformed(null));
        assertEquals(1, ask.linkTargets.size(), "the other chapter only");
        edtRun(() -> window.actionFor("menu.insert.scene-break").actionPerformed(null));
        Chapter c = current();
        Block.Paragraph p = c.blocks().stream().filter(b -> b instanceof Block.Paragraph x && x.plainText().startsWith("See"))
                .map(b -> (Block.Paragraph) b).findFirst().orElseThrow(() -> new AssertionError(c.blocks().toString()));
        assertTrue(p.content().contains(new Inline.Footnote("A note.")));
        assertTrue(p.content().contains(new Inline.Text("See", Set.of(), Link.chapter(target))), p.content().toString());
        assertTrue(c.blocks().stream().anyMatch(b -> b instanceof Block.SceneBreak));
    }

    @Test
    void picturesAreAddedWithTheirDescription() throws Exception {
        newBook("Book");
        Path png = Files.write(dir.resolve("Lamp Post.png"), TestBooks.png(40, 30, Color.ORANGE));
        ask.picture = png;
        ask.pictureAnswer = p -> new Block.Picture(p.resource(), "A lamp post", "", PictureSize.SMALL);
        edtRun(() -> window.actionFor("menu.insert.picture").actionPerformed(null));
        Chapter c = current();
        Block.Picture pic = c.blocks().stream().filter(b -> b instanceof Block.Picture).map(b -> (Block.Picture) b)
                .findFirst().orElseThrow();
        assertEquals("A lamp post", pic.description());
        assertTrue(window.book().resource(pic.resource()).isPresent());
        assertTrue(ask.asked.contains("picture " + pic.resource()), ask.asked.toString());
    }

    @Test
    void aCancelledPictureLeavesNothingBehind() throws Exception {
        newBook("Book");
        ask.picture = Files.write(dir.resolve("x.png"), TestBooks.png(10, 10, Color.RED));
        ask.pictureAnswer = p -> null;
        edtRun(() -> window.actionFor("menu.insert.picture").actionPerformed(null));
        assertTrue(window.book().resources().isEmpty());
    }

    @Test
    void stylesAndMarksAreApplied() throws Exception {
        newBook("Book");
        edtRun(() -> window.editor().setCaretPosition(window.editor().getDocument().getLength()));
        type("A quotation");
        edtRun(() -> window.editor().select(window.editor().getDocument().getLength() - 9, window.editor().getDocument().getLength()));
        edtRun(() -> window.actionFor("menu.format.italic").actionPerformed(null));
        edtRun(() -> window.editor().applyStyle(ParaStyle.QUOTE));
        Chapter c = current();
        Block.Paragraph p = (Block.Paragraph) c.blocks().getLast();
        assertEquals(ParaStyle.QUOTE, p.style());
        assertTrue(p.content().contains(new Inline.Text("quotation", Set.of(Mark.ITALIC), "")), p.content().toString());
    }

    @Test
    void bookDetailsChangeTheTitleAndCanMakeACover() throws Exception {
        newBook("Draft");
        ask.details = info -> info.withTitle("Final Title").withDescription("About the book.");
        ask.detailsMakesCover = true;
        BufferedImage img = new BufferedImage(160, 240, BufferedImage.TYPE_INT_RGB);
        ask.cover = img;
        edtRun(() -> window.details(0));
        assertEquals("Final Title", window.book().info().title());
        assertTrue(window.book().cover().isPresent());
        assertTrue(window.getTitle().contains("Final Title"));
    }

    @Test
    void theOutlineOpensTheCoverAndTitlePages() throws Exception {
        newBook("Book");
        edtRun(() -> window.outline().select(OutlinePanel.Kind.COVER, null));
        assertEquals(OutlinePanel.Kind.COVER, window.shownKind());
        assertNull(window.currentChapterId());
        assertFalse(edt(() -> window.actionFor("menu.insert.picture").isEnabled()));
        edtRun(() -> window.outline().select(OutlinePanel.Kind.TITLE_PAGE, null));
        assertEquals(OutlinePanel.Kind.TITLE_PAGE, window.shownKind());
        edtRun(() -> window.outline().select(OutlinePanel.Kind.CHAPTER, window.book().chapters().getFirst().id()));
        assertTrue(edt(() -> window.actionFor("menu.insert.picture").isEnabled()));
    }

    @Test
    void aDedicationCanBeAddedAndRemoved() throws Exception {
        newBook("Book");
        edtRun(window::addDedication);
        assertTrue(window.book().dedication().isPresent());
        assertEquals(Book.DEDICATION_ID, window.currentChapterId());
        type("For Mia");
        assertEquals("For Mia", ((Block.Paragraph) current().blocks().getFirst()).plainText());
        edtRun(window::deleteChapter);
        assertTrue(window.book().dedication().isEmpty());
    }

    @Test
    void checkBookShowsOwnFindingsAndThenEpubCheck() throws Exception {
        newBook("");
        edtRun(window::checkBook);
        CheckDialog d = window.checkDialog();
        assertNotNull(d);
        assertTrue(edt(() -> d.shownFindings().stream().anyMatch(f -> f.kind() == Finding.Kind.NO_TITLE)));
        background.submit(() -> {
        }).get(5, TimeUnit.SECONDS);
        // let the background check finish and come back to the Swing thread
        for (int i = 0; i < 50 && edt(() -> d.shownFindings().stream()
                .noneMatch(f -> f.kind() == Finding.Kind.EPUBCHECK_WARNING)); i++) {
            Thread.sleep(50);
        }
        assertTrue(edt(() -> d.shownFindings().stream().anyMatch(f -> f.kind() == Finding.Kind.EPUBCHECK_WARNING)));
        edtRun(() -> d.setVisible(false));
    }

    @Test
    void checkBookLeadsToTheProblem() throws Exception {
        newBook("Book");
        edtRun(window::addChapter);
        String empty = window.currentChapterId();
        edtRun(() -> window.outline().select(OutlinePanel.Kind.COVER, null));
        edtRun(() -> window.act(new Finding(Finding.Kind.EMPTY_CHAPTER, empty, "")));
        assertEquals(empty, window.currentChapterId());
        edtRun(() -> window.act(new Finding(Finding.Kind.NO_DESCRIPTION, "", "")));
        assertTrue(ask.asked.contains("details 1"), ask.asked.toString());
    }

    @Test
    void theStatusLineCountsWordsAndShowsReadiness() throws Exception {
        newBook("Book");
        edtRun(() -> window.editor().setCaretPosition(window.editor().getDocument().getLength()));
        type("one two three four");
        edtRun(window::commit);
        edtRun(() -> window.outline().select(OutlinePanel.Kind.CHAPTER, window.book().chapters().getFirst().id()));
        String status = window.statusText();
        assertTrue(status.startsWith("Chapter 1 of 1"), status);
        assertTrue(status.contains("whole book"), status);
        assertTrue(window.readinessText().contains("Ready to publish"), window.readinessText());
    }

    @Test
    void autosaveWritesBooksThatHaveAFile() throws Exception {
        newBook("Book");
        edtRun(window::autosave);
        assertTrue(window.dirty(), "a new book is not saved before it has a file");
        ask.saveAs = dir.resolve("auto.epub");
        assertTrue(edt(window::save));
        type("Changed.");
        edtRun(window::commit);
        assertTrue(window.dirty());
        edtRun(window::autosave);
        assertFalse(window.dirty());
    }

    @Test
    void theLookOfTheBookCanBeChanged() throws Exception {
        newBook("Book");
        ask.saveAs = dir.resolve("look.epub");
        assertTrue(edt(window::save));
        edtRun(() -> {
            javax.swing.JMenuBar bar = window.getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) {
                findAndClick(bar.getMenu(i), "menu.format.look.poetry");
            }
        });
        assertEquals(Look.POETRY, window.book().look());
        assertTrue(window.dirty());
    }

    private static void findAndClick(javax.swing.JMenuItem item, String name) {
        if (name.equals(item.getName())) {
            item.doClick();
            return;
        }
        if (item instanceof javax.swing.JMenu m) {
            for (int i = 0; i < m.getItemCount(); i++) {
                if (m.getItem(i) != null) findAndClick(m.getItem(i), name);
            }
        }
    }
}
