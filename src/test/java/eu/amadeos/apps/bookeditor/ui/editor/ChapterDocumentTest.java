package eu.amadeos.apps.bookeditor.ui.editor;

import eu.amadeos.apps.bookeditor.TestBooks;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterDocumentTest {
    static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) throw error.get();
        return result.get();
    }

    @Test
    void everyChapterOfTheTestBookComesBackUnchanged() throws Exception {
        Book book = TestBooks.full();
        for (Chapter c : book.chapters()) {
            List<Block> back = onEdt(() -> ChapterDocument.fromChapter(c, name -> null).toBlocks());
            assertEquals(c.blocks(), back, c.title());
        }
    }

    @Test
    void headingsAreBoldWithoutTheTextBeingMarkedBold() throws Exception {
        Chapter c = new Chapter("x1", true, List.of(TestBooks.p(ParaStyle.CHAPTER_TITLE, "Title")));
        List<Block> back = onEdt(() -> ChapterDocument.fromChapter(c, n -> null).toBlocks());
        Block.Paragraph p = (Block.Paragraph) back.getFirst();
        assertEquals(Set.of(), ((Inline.Text) p.content().getFirst()).marks());
    }

    @Test
    void enterAfterAHeadingGoesBackToText() throws Exception {
        Chapter c = new Chapter("x2", true, List.of(TestBooks.p(ParaStyle.CHAPTER_TITLE, "Title")));
        List<Block> back = onEdt(() -> {
            ChapterEditor editor = new ChapterEditor();
            editor.setChapter(ChapterDocument.fromChapter(c, n -> null));
            editor.setCaretPosition(5);
            editor.newParagraph();
            editor.replaceSelection("Body");
            return editor.chapter().toBlocks();
        });
        assertEquals(List.of(TestBooks.p(ParaStyle.CHAPTER_TITLE, "Title"), TestBooks.text("Body")), back);
    }

    @Test
    void enterInAPoemStaysInThePoem() throws Exception {
        Chapter c = new Chapter("x3", true, List.of(TestBooks.p(ParaStyle.POEM, "Line one")));
        List<Block> back = onEdt(() -> {
            ChapterEditor editor = new ChapterEditor();
            editor.setChapter(ChapterDocument.fromChapter(c, n -> null));
            editor.setCaretPosition(8);
            editor.newParagraph();
            editor.replaceSelection("Line two");
            return editor.chapter().toBlocks();
        });
        assertEquals(List.of(TestBooks.p(ParaStyle.POEM, "Line one"), TestBooks.p(ParaStyle.POEM, "Line two")), back);
    }

    @Test
    void stylesMarksFootnotesAndBlocksCanBeAdded() throws Exception {
        Chapter c = new Chapter("x4", true, List.of(TestBooks.text("Hello world")));
        List<Block> back = onEdt(() -> {
            ChapterEditor editor = new ChapterEditor();
            editor.setChapter(ChapterDocument.fromChapter(c, n -> null));
            editor.select(6, 11);
            editor.toggle(Mark.ITALIC);
            editor.setCaretPosition(11);
            editor.insertFootnote("A note.");
            editor.insertBlock(new Block.SceneBreak());
            editor.replaceSelection("After");
            editor.setCaretPosition(0);
            editor.applyStyle(ParaStyle.QUOTE);
            return editor.chapter().toBlocks();
        });
        assertEquals(3, back.size(), back.toString());
        Block.Paragraph first = (Block.Paragraph) back.getFirst();
        assertEquals(ParaStyle.QUOTE, first.style());
        assertEquals(List.of(new Inline.Text("Hello "), new Inline.Text("world", Set.of(Mark.ITALIC), ""),
                new Inline.Footnote("A note.")), first.content());
        assertInstanceOf(Block.SceneBreak.class, back.get(1));
        assertEquals(TestBooks.text("After"), back.get(2));
    }

    @Test
    void togglingBoldTwiceLeavesPlainText() throws Exception {
        Chapter c = new Chapter("x5", true, List.of(TestBooks.text("Hello")));
        List<Block> back = onEdt(() -> {
            ChapterEditor editor = new ChapterEditor();
            editor.setChapter(ChapterDocument.fromChapter(c, n -> null));
            editor.selectAll();
            editor.toggle(Mark.BOLD);
            assertTrue(editor.has(Mark.BOLD));
            editor.toggle(Mark.BOLD);
            return editor.chapter().toBlocks();
        });
        assertEquals(List.of(TestBooks.text("Hello")), back);
    }

    @Test
    void linksCanBeSetAndRemoved() throws Exception {
        Chapter c = new Chapter("x6", true, List.of(TestBooks.text("See chapter two.")));
        List<Block> back = onEdt(() -> {
            ChapterEditor editor = new ChapterEditor();
            editor.setChapter(ChapterDocument.fromChapter(c, n -> null));
            editor.setLink(4, 15, "chapter:abc", "");
            editor.setCaretPosition(8);
            assertEquals("chapter:abc", editor.currentLink());
            int[] range = editor.linkRange();
            assertEquals(4, range[0]);
            assertEquals(15, range[1]);
            return editor.chapter().toBlocks();
        });
        assertEquals(List.of(new Block.Paragraph(ParaStyle.TEXT, List.of(new Inline.Text("See "),
                new Inline.Text("chapter two", Set.of(), "chapter:abc"), new Inline.Text(".")))), back);
    }
}
