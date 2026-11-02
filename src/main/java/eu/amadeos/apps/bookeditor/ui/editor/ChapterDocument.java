package eu.amadeos.apps.bookeditor.ui.editor;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.KeptKind;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The text of one chapter while it is edited. Paragraphs carry their {@link ParaStyle}; characters carry bold,
 * italic, superscript, subscript and links; footnotes, pictures, scene breaks and kept content are one object
 * character each.
 * {@link #fromChapter} and {@link #toBlocks} convert between this and the book model without loss.
 */
public final class ChapterDocument extends DefaultStyledDocument {
    /** The object character Swing shows icons for. */
    public static final String OBJECT = "￼";

    private final String chapterId;
    private final Function<String, BufferedImage> images;

    /**
     * @param images the pictures of the book by resource name, for showing them; may answer null
     */
    public ChapterDocument(String chapterId, Function<String, BufferedImage> images) {
        this.chapterId = chapterId;
        this.images = images;
    }

    public String chapterId() {
        return chapterId;
    }

    // --8<-- [start:from]
    public static ChapterDocument fromChapter(Chapter chapter, Function<String, BufferedImage> images) {
        ChapterDocument doc = new ChapterDocument(chapter.id(), images);
        List<Block> blocks = chapter.blocks().isEmpty() ? List.of(new Block.Paragraph(ParaStyle.TEXT, "")) : chapter.blocks();
        try {
            for (int i = 0; i < blocks.size(); i++) {
                int start = doc.getLength();
                if (i > 0) {
                    doc.insertString(start, "\n", plainAttributes());
                    start++;
                }
                switch (blocks.get(i)) {
                    case Block.Paragraph p -> {
                        for (Inline inline : p.content()) doc.insertInline(doc.getLength(), inline);
                        doc.styleParagraph(start, p.style(), false);
                    }
                    case Block.Picture pic -> {
                        doc.insertString(start, OBJECT, doc.pictureAttributes(pic));
                        doc.styleParagraph(start, ParaStyle.TEXT, true);
                    }
                    case Block.SceneBreak ignored -> {
                        doc.insertString(start, OBJECT, sceneBreakAttributes());
                        doc.styleParagraph(start, ParaStyle.TEXT, true);
                    }
                    case Block.Kept k -> {
                        doc.insertString(start, OBJECT, keptAttributes(k));
                        doc.styleParagraph(start, ParaStyle.TEXT, true);
                    }
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        doc.renumberFootnotes();
        return doc;
    }
    // --8<-- [end:from]

    private static SimpleAttributeSet plainAttributes() {
        return new SimpleAttributeSet();
    }

    void insertInline(int pos, Inline inline) throws BadLocationException {
        switch (inline) {
            case Inline.Text t -> insertString(pos, t.text(), textAttributes(t.marks(), t.link()));
            case Inline.Footnote f -> insertString(pos, OBJECT, footnoteAttributes(f.text()));
        }
    }

    public static SimpleAttributeSet textAttributes(Set<Mark> marks, String link) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        if (marks.contains(Mark.BOLD)) StyleConstants.setBold(a, true);
        if (marks.contains(Mark.ITALIC)) StyleConstants.setItalic(a, true);
        if (marks.contains(Mark.SUPERSCRIPT)) StyleConstants.setSuperscript(a, true);
        if (marks.contains(Mark.SUBSCRIPT)) StyleConstants.setSubscript(a, true);
        if (link != null && !link.isEmpty()) {
            a.addAttribute(EditorStyles.LINK, link);
            StyleConstants.setUnderline(a, true);
            StyleConstants.setForeground(a, EditorStyles.LINK_COLOUR);
        }
        return a;
    }

    public static SimpleAttributeSet footnoteAttributes(String text) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(EditorStyles.FOOTNOTE, text == null ? "" : text);
        StyleConstants.setIcon(a, new FootnoteIcon());
        return a;
    }

    public SimpleAttributeSet pictureAttributes(Block.Picture picture) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(EditorStyles.PICTURE, picture);
        StyleConstants.setIcon(a, new PictureIcon(picture, images.apply(picture.resource())));
        return a;
    }

    public static SimpleAttributeSet sceneBreakAttributes() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(EditorStyles.SCENE_BREAK, Boolean.TRUE);
        StyleConstants.setIcon(a, new SceneBreakIcon());
        return a;
    }

    /** The label of a kept box ("Table") and the hint after it; the main window gives them in the user's language. */
    private static volatile Function<KeptKind, String> keptLabels = KeptKind::key;
    private static volatile String keptHint = "";

    public static void setKeptTexts(Function<KeptKind, String> labels, String hint) {
        keptLabels = labels;
        keptHint = hint;
    }

    public static SimpleAttributeSet keptAttributes(Block.Kept kept) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(EditorStyles.KEPT, kept);
        StyleConstants.setIcon(a, new KeptIcon(kept, keptLabels.apply(kept.kind()), keptHint));
        return a;
    }

    // ---------------------------------------------------------------- paragraph styles

    /** The style of the paragraph at {@code offset}. */
    public ParaStyle styleAt(int offset) {
        Object s = getParagraphElement(offset).getAttributes().getAttribute(EditorStyles.STYLE);
        return s instanceof ParaStyle p ? p : ParaStyle.TEXT;
    }

    /** Gives the paragraphs from {@code start} to {@code end} a style, with the look the editor shows for it. */
    public void setStyle(int start, int end, ParaStyle style) {
        int pos = start;
        while (true) {
            Element para = getParagraphElement(pos);
            styleParagraph(para.getStartOffset(), style, objectParagraph(para));
            if (para.getEndOffset() > end || para.getEndOffset() >= getLength() + 1) break;
            pos = para.getEndOffset();
        }
    }

    void styleParagraph(int offset, ParaStyle style, boolean object) {
        Element para = getParagraphElement(offset);
        setParagraphAttributes(para.getStartOffset(), Math.max(1, para.getEndOffset() - para.getStartOffset()),
                EditorStyles.paragraph(object ? ParaStyle.CENTERED : style, object ? ParaStyle.TEXT : style), true);
    }

    /** A paragraph that holds a picture or a scene break. */
    public boolean objectParagraph(Element para) {
        for (int i = 0; i < para.getElementCount(); i++) {
            AttributeSet a = para.getElement(i).getAttributes();
            if (a.isDefined(EditorStyles.PICTURE) || a.isDefined(EditorStyles.SCENE_BREAK) || a.isDefined(EditorStyles.KEPT)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- back to the model

    // --8<-- [start:to]
    public List<Block> toBlocks() {
        List<Block> blocks = new ArrayList<>();
        Element root = getDefaultRootElement();
        try {
            for (int p = 0; p < root.getElementCount(); p++) {
                Element para = root.getElement(p);
                Object s = para.getAttributes().getAttribute(EditorStyles.STYLE);
                ParaStyle style = s instanceof ParaStyle ps ? ps : ParaStyle.TEXT;
                List<Inline> inlines = new ArrayList<>();
                boolean hadObject = false;
                for (int i = 0; i < para.getElementCount(); i++) {
                    Element leaf = para.getElement(i);
                    AttributeSet a = leaf.getAttributes();
                    int start = leaf.getStartOffset();
                    int end = Math.min(leaf.getEndOffset(), getLength());
                    String text = end > start ? getText(start, end - start) : "";
                    if (a.isDefined(EditorStyles.PICTURE) || a.isDefined(EditorStyles.SCENE_BREAK) || a.isDefined(EditorStyles.KEPT)) {
                        if (!inlines.isEmpty()) blocks.add(new Block.Paragraph(ParaStyle.TEXT, inlines));
                        inlines = new ArrayList<>();
                        hadObject = true;
                        blocks.add(a.isDefined(EditorStyles.PICTURE) ? (Block.Picture) a.getAttribute(EditorStyles.PICTURE)
                                : a.isDefined(EditorStyles.KEPT) ? (Block.Kept) a.getAttribute(EditorStyles.KEPT)
                                : new Block.SceneBreak());
                        continue;
                    }
                    if (a.isDefined(EditorStyles.FOOTNOTE)) {
                        inlines.add(new Inline.Footnote((String) a.getAttribute(EditorStyles.FOOTNOTE)));
                        continue;
                    }
                    text = text.replace("\n", "").replace(OBJECT, "");
                    if (text.isEmpty()) continue;
                    inlines.add(new Inline.Text(text, marks(a), link(a)));
                }
                if (hadObject) {
                    if (!inlines.isEmpty()) blocks.add(new Block.Paragraph(ParaStyle.TEXT, inlines));
                } else {
                    blocks.add(new Block.Paragraph(style, inlines));
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        // The empty paragraph Swing keeps at the end of a document is not part of the chapter
        while (blocks.size() > 1 && blocks.getLast() instanceof Block.Paragraph last && last.isEmpty()
                && last.style() == ParaStyle.TEXT) {
            blocks.removeLast();
        }
        return blocks;
    }
    // --8<-- [end:to]

    /** The marks set on the characters themselves, not the ones a heading style shows them with. */
    static Set<Mark> marks(AttributeSet a) {
        Set<Mark> marks = EnumSet.noneOf(Mark.class);
        if (local(a, StyleConstants.Bold)) marks.add(Mark.BOLD);
        if (local(a, StyleConstants.Italic)) marks.add(Mark.ITALIC);
        if (local(a, StyleConstants.Superscript)) marks.add(Mark.SUPERSCRIPT);
        if (local(a, StyleConstants.Subscript)) marks.add(Mark.SUBSCRIPT);
        return marks;
    }

    private static boolean local(AttributeSet a, Object key) {
        return a.isDefined(key) && Boolean.TRUE.equals(a.getAttribute(key));
    }

    public static String link(AttributeSet a) {
        Object l = a.getAttribute(EditorStyles.LINK);
        return l instanceof String s ? s : "";
    }

    /** The chapter title: the text of the first chapter-title paragraph. */
    public String title() {
        Element root = getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            if (para.getAttributes().getAttribute(EditorStyles.STYLE) == ParaStyle.CHAPTER_TITLE) {
                try {
                    return getText(para.getStartOffset(), para.getEndOffset() - para.getStartOffset())
                            .replace(OBJECT, "").strip();
                } catch (BadLocationException e) {
                    return "";
                }
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- footnotes

    /** Numbers the footnote icons 1, 2, 3 in the order they appear; returns how many there are. */
    public int renumberFootnotes() {
        int n = 0;
        Element root = getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            for (int i = 0; i < para.getElementCount(); i++) {
                AttributeSet a = para.getElement(i).getAttributes();
                if (a.isDefined(EditorStyles.FOOTNOTE) && StyleConstants.getIcon(a) instanceof FootnoteIcon icon) {
                    icon.setNumber(++n);
                }
            }
        }
        return n;
    }

    /** The leaf element at an offset if it is a footnote, a picture or a scene break. */
    public Element objectAt(int offset) {
        if (offset < 0 || offset >= getLength()) return null;
        Element leaf = getCharacterElement(offset);
        AttributeSet a = leaf.getAttributes();
        return a.isDefined(EditorStyles.FOOTNOTE) || a.isDefined(EditorStyles.PICTURE) || a.isDefined(EditorStyles.SCENE_BREAK)
                || a.isDefined(EditorStyles.KEPT) ? leaf : null;
    }

    /** Replaces the object character at {@code offset} with one carrying new attributes (an edited picture or note). */
    public void replaceObject(int offset, SimpleAttributeSet attributes) {
        try {
            remove(offset, 1);
            insertString(offset, OBJECT, attributes);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        renumberFootnotes();
    }
}
