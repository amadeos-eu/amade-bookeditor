package eu.amadeos.apps.bookeditor.ui.editor;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;

import javax.swing.AbstractAction;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The text area for one chapter at a time. Enter after a heading goes back to body text; double-clicking a
 * picture or a footnote opens its settings; links and footnotes show their target or text as a tooltip.
 */
public final class ChapterEditor extends JTextPane {
    /** What double-clicking or pressing Enter on an object asks for: the offset of its character. */
    public interface ObjectHandler {
        void editPicture(int offset, Block.Picture picture);

        void editFootnote(int offset, String text);
    }

    private final UndoManager undo = new UndoManager();
    private ObjectHandler handler;
    private Function<String, String> linkTitles = l -> l;

    public ChapterEditor() {
        setEditorKit(new BookEditorKit());
        setMargin(new Insets(28, 40, 40, 40));
        setBackground(Color.WHITE);
        setSelectionColor(new Color(0xB4, 0xD5, 0xFE));
        setCaretColor(Color.BLACK);
        setToolTipText("");
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "book-break");
        getActionMap().put("book-break", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                newParagraph();
            }
        });
        // Shift+Enter would make a line break inside a paragraph, which EPUB paragraphs here do not have
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "book-break");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int offset = objectOffsetAt(e.getPoint());
                    if (offset >= 0 && editObject(offset)) e.consume();
                }
            }
        });
    }

    public void setObjectHandler(ObjectHandler handler) {
        this.handler = handler;
    }

    /** Turns a chapter link's target into the chapter's title, for tooltips. */
    public void setLinkTitles(Function<String, String> titles) {
        linkTitles = titles;
    }

    public void setChapter(ChapterDocument doc) {
        setDocument(doc);
        undo.discardAllEdits();
        doc.addUndoableEditListener((UndoableEditEvent e) -> undo.addEdit(e.getEdit()));
        setCaretPosition(0);
    }

    public ChapterDocument chapter() {
        return (ChapterDocument) getDocument();
    }

    // ---------------------------------------------------------------- undo

    public boolean canUndo() {
        return undo.canUndo();
    }

    public void undo() {
        try {
            if (undo.canUndo()) undo.undo();
        } catch (CannotUndoException ignored) {
            // nothing left
        }
        chapter().renumberFootnotes();
    }

    public void redo() {
        try {
            if (undo.canRedo()) undo.redo();
        } catch (CannotRedoException ignored) {
            // nothing left
        }
        chapter().renumberFootnotes();
    }

    // ---------------------------------------------------------------- styles and marks

    /** The style of the paragraph the caret is in. */
    public ParaStyle currentStyle() {
        return chapter().styleAt(getCaretPosition());
    }

    public void applyStyle(ParaStyle style) {
        int start = getSelectionStart(), end = getSelectionEnd();
        chapter().setStyle(start, end, style);
        requestFocusInWindow();
    }

    /** Turns a mark on for the selection, or off when all of it has it already. */
    public void toggle(Mark mark) {
        int start = getSelectionStart(), end = getSelectionEnd();
        Object key = switch (mark) {
            case BOLD -> StyleConstants.Bold;
            case ITALIC -> StyleConstants.Italic;
            case SUPERSCRIPT -> StyleConstants.Superscript;
            case SUBSCRIPT -> StyleConstants.Subscript;
        };
        boolean on = !allHave(start, end, key);
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(key, on);
        if (on && mark == Mark.SUPERSCRIPT) StyleConstants.setSubscript(a, false);
        if (on && mark == Mark.SUBSCRIPT) StyleConstants.setSuperscript(a, false);
        if (start == end) {
            getInputAttributes().addAttributes(a);
        } else {
            chapter().setCharacterAttributes(start, end - start, a, false);
            if (!on) removeLocal(start, end, key);
        }
        requestFocusInWindow();
    }

    /** Whether the selection (or, without one, the typing position) has a mark. */
    public boolean has(Mark mark) {
        Object key = switch (mark) {
            case BOLD -> StyleConstants.Bold;
            case ITALIC -> StyleConstants.Italic;
            case SUPERSCRIPT -> StyleConstants.Superscript;
            case SUBSCRIPT -> StyleConstants.Subscript;
        };
        int start = getSelectionStart(), end = getSelectionEnd();
        if (start == end) return Boolean.TRUE.equals(getInputAttributes().getAttribute(key));
        return allHave(start, end, key);
    }

    private boolean allHave(int start, int end, Object key) {
        if (start == end) return Boolean.TRUE.equals(getInputAttributes().getAttribute(key));
        for (int pos = start; pos < end; ) {
            Element leaf = chapter().getCharacterElement(pos);
            if (!Boolean.TRUE.equals(leaf.getAttributes().getAttribute(key))) return false;
            pos = leaf.getEndOffset();
        }
        return true;
    }

    /** Takes a mark off the characters, so that the paragraph style (a bold heading) shows again. */
    private void removeLocal(int start, int end, Object key) {
        for (int pos = start; pos < end; ) {
            Element leaf = chapter().getCharacterElement(pos);
            int s = Math.max(start, leaf.getStartOffset()), e = Math.min(end, leaf.getEndOffset());
            SimpleAttributeSet copy = new SimpleAttributeSet(leaf.getAttributes().copyAttributes());
            copy.removeAttribute(key);
            chapter().setCharacterAttributes(s, e - s, copy, true);
            pos = leaf.getEndOffset();
        }
    }

    // ---------------------------------------------------------------- links

    /** The link at the caret or on the selection, empty if there is none. */
    public String currentLink() {
        int pos = getSelectionStart() < getSelectionEnd() ? getSelectionStart() : Math.max(0, getCaretPosition() - 1);
        if (chapter().getLength() == 0) return "";
        return ChapterDocument.link(chapter().getCharacterElement(pos).getAttributes());
    }

    /** The range of the link around the caret, or the selection when there is no link. */
    public int[] linkRange() {
        int start = getSelectionStart(), end = getSelectionEnd();
        String link = currentLink();
        if (link.isEmpty() || start < end) return new int[]{start, end};
        int s = getCaretPosition(), e = s;
        while (s > 0 && link.equals(ChapterDocument.link(chapter().getCharacterElement(s - 1).getAttributes()))) s--;
        while (e < chapter().getLength() && link.equals(ChapterDocument.link(chapter().getCharacterElement(e).getAttributes()))) e++;
        return new int[]{s, e};
    }

    /** Sets (or with an empty target removes) the link on a range; with no range, inserts {@code text} as a link. */
    public void setLink(int start, int end, String target, String text) {
        ChapterDocument doc = chapter();
        try {
            if (start == end && !text.isEmpty() && !target.isEmpty()) {
                doc.insertString(start, text, ChapterDocument.textAttributes(java.util.Set.of(), target));
                setCaretPosition(start + text.length());
                getInputAttributes().removeAttribute(EditorStyles.LINK);
                getInputAttributes().removeAttribute(StyleConstants.Underline);
                getInputAttributes().removeAttribute(StyleConstants.Foreground);
                return;
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        for (int pos = start; pos < end; ) {
            Element leaf = doc.getCharacterElement(pos);
            int s = Math.max(start, leaf.getStartOffset()), e = Math.min(end, leaf.getEndOffset());
            MutableAttributeSet a = new SimpleAttributeSet(leaf.getAttributes().copyAttributes());
            a.removeAttribute(EditorStyles.LINK);
            a.removeAttribute(StyleConstants.Underline);
            a.removeAttribute(StyleConstants.Foreground);
            if (!target.isEmpty() && !a.isDefined(EditorStyles.FOOTNOTE) && !a.isDefined(EditorStyles.PICTURE)) {
                a.addAttributes(ChapterDocument.textAttributes(java.util.Set.of(), target));
            }
            doc.setCharacterAttributes(s, e - s, a, true);
            pos = leaf.getEndOffset();
        }
    }

    // ---------------------------------------------------------------- objects

    public void insertFootnote(String text) {
        replaceSelection("");
        int pos = getCaretPosition();
        try {
            chapter().insertString(pos, ChapterDocument.OBJECT, ChapterDocument.footnoteAttributes(text));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        chapter().renumberFootnotes();
        setCaretPosition(pos + 1);
    }

    /** Puts a picture or a scene break on a line of its own after the caret's paragraph (or into an empty one). */
    public void insertBlock(Block block) {
        ChapterDocument doc = chapter();
        Element para = doc.getParagraphElement(getSelectionEnd());
        try {
            int pos;
            boolean empty = para.getEndOffset() - para.getStartOffset() <= 1 && !doc.objectParagraph(para);
            if (empty) {
                pos = para.getStartOffset();
            } else {
                pos = para.getEndOffset() - 1;
                doc.insertString(pos, "\n", new SimpleAttributeSet());
                pos++;
            }
            SimpleAttributeSet a = block instanceof Block.Picture p ? doc.pictureAttributes(p)
                    : ChapterDocument.sceneBreakAttributes();
            doc.insertString(pos, ChapterDocument.OBJECT, a);
            doc.styleParagraph(pos, ParaStyle.TEXT, true);
            // A text paragraph after it, so writing goes on below
            Element objectPara = doc.getParagraphElement(pos);
            int after = objectPara.getEndOffset();
            if (after > doc.getLength() || doc.objectParagraph(doc.getParagraphElement(after))) {
                doc.insertString(objectPara.getEndOffset() - 1, "\n", new SimpleAttributeSet());
                after = objectPara.getEndOffset();
            }
            doc.styleParagraph(Math.min(after, doc.getLength()), ParaStyle.TEXT, false);
            setCaretPosition(Math.min(after, doc.getLength()));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The offset of the object under the mouse, or -1. */
    int objectOffsetAt(Point p) {
        int pos = viewToModel2D(p);
        for (int offset : new int[]{pos, pos - 1}) {
            Element leaf = chapter().objectAt(offset);
            if (leaf == null) continue;
            try {
                java.awt.geom.Rectangle2D r = modelToView2D(offset);
                java.awt.geom.Rectangle2D next = modelToView2D(offset + 1);
                if (r != null && next != null && p.x >= r.getX() - 2 && p.x <= Math.max(next.getX(), r.getX() + 8) + 2) {
                    return offset;
                }
            } catch (BadLocationException e) {
                return -1;
            }
        }
        return -1;
    }

    /** Opens the settings of the picture or footnote at {@code offset}; false if there is none. */
    public boolean editObject(int offset) {
        Element leaf = chapter().objectAt(offset);
        if (leaf == null || handler == null) return false;
        AttributeSet a = leaf.getAttributes();
        if (a.isDefined(EditorStyles.PICTURE)) {
            handler.editPicture(offset, (Block.Picture) a.getAttribute(EditorStyles.PICTURE));
            return true;
        }
        if (a.isDefined(EditorStyles.FOOTNOTE)) {
            handler.editFootnote(offset, (String) a.getAttribute(EditorStyles.FOOTNOTE));
            return true;
        }
        return false;
    }

    /** The picture in or next to the selection, with its offset. */
    public Optional<Integer> pictureNearCaret() {
        int[] candidates = {getSelectionStart(), getCaretPosition() - 1, getCaretPosition()};
        for (int offset : candidates) {
            Element leaf = chapter().objectAt(offset);
            if (leaf != null && leaf.getAttributes().isDefined(EditorStyles.PICTURE)) return Optional.of(offset);
        }
        return Optional.empty();
    }

    public void replacePicture(int offset, Block.Picture picture) {
        chapter().replaceObject(offset, chapter().pictureAttributes(picture));
    }

    public void replaceFootnote(int offset, String text) {
        chapter().replaceObject(offset, ChapterDocument.footnoteAttributes(text));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int pos = viewToModel2D(e.getPoint());
        if (pos < 0 || chapter() == null) return null;
        int offset = objectOffsetAt(e.getPoint());
        if (offset >= 0) {
            AttributeSet a = chapter().getCharacterElement(offset).getAttributes();
            if (a.isDefined(EditorStyles.FOOTNOTE)) return wrap((String) a.getAttribute(EditorStyles.FOOTNOTE));
            if (a.isDefined(EditorStyles.PICTURE)) {
                Block.Picture p = (Block.Picture) a.getAttribute(EditorStyles.PICTURE);
                return wrap(p.description().isEmpty() ? null : p.description());
            }
        }
        String link = ChapterDocument.link(chapter().getCharacterElement(pos).getAttributes());
        if (link.isEmpty()) return null;
        return Link.chapterId(link).map(id -> "→ " + linkTitles.apply(id)).orElse(link);
    }

    private static String wrap(String text) {
        if (text == null || text.isEmpty()) return null;
        return "<html><div style='width:300px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>";
    }

    // ---------------------------------------------------------------- Enter

    // --8<-- [start:enter]
    /** Enter: a new paragraph; after a heading, a motto or a picture it is body text again. */
    void newParagraph() {
        ChapterDocument doc = chapter();
        Element para = doc.getParagraphElement(getSelectionStart());
        ParaStyle style = doc.styleAt(getSelectionStart());
        boolean object = doc.objectParagraph(para);
        boolean atStart = getSelectionStart() == para.getStartOffset() && getSelectionEnd() == getSelectionStart();
        replaceSelection("\n");
        int pos = getCaretPosition();
        if (object || !style.continues()) {
            if (atStart && !object) {
                // Enter at the start of a heading leaves an empty text line above it
                doc.styleParagraph(pos - 1, ParaStyle.TEXT, false);
                doc.styleParagraph(pos, style, false);
            } else {
                doc.styleParagraph(pos, ParaStyle.TEXT, doc.objectParagraph(doc.getParagraphElement(pos)));
            }
        }
        getInputAttributes().removeAttributes(getInputAttributes());
    }
    // --8<-- [end:enter]

    /** Keeps the editor's own attributes from spreading into newly typed text. */
    private static final class BookEditorKit extends StyledEditorKit {
        @Override
        protected void createInputAttributes(Element element, MutableAttributeSet set) {
            super.createInputAttributes(element, set);
            set.removeAttribute(EditorStyles.FOOTNOTE);
            set.removeAttribute(EditorStyles.PICTURE);
            set.removeAttribute(EditorStyles.SCENE_BREAK);
            set.removeAttribute(StyleConstants.IconAttribute);
        }
    }
}
