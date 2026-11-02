package eu.amadeos.apps.bookeditor.ui.editor;

import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;

import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;

/**
 * The attribute keys the editor stores its own information under, and how each paragraph style looks while
 * writing. The look on screen is a hint only; the book's look (its style sheet) decides how it is read.
 */
public final class EditorStyles {
    /** Paragraph attribute: the {@link ParaStyle}. */
    public static final Object STYLE = new Key("book-style");
    /** Character attribute: the target of a link. */
    public static final Object LINK = new Key("book-link");
    /** Character attribute of a footnote's object character: the text of the note. */
    public static final Object FOOTNOTE = new Key("book-footnote");
    /** Character attribute of a picture's object character: the {@code Block.Picture}. */
    public static final Object PICTURE = new Key("book-picture");
    /** Character attribute of a scene break's object character. */
    public static final Object SCENE_BREAK = new Key("book-scene-break");

    public static final Color LINK_COLOUR = new Color(0x1A, 0x5F, 0xB4);
    public static final Color HEADING_COLOUR = new Color(0x1F, 0x3A, 0x5F);
    public static final Color NOTE_COLOUR = new Color(0x00, 0x80, 0x80);
    public static final String SERIF = "DejaVu Serif";
    public static final String SANS = "DejaVu Sans";
    public static final String MONO = "DejaVu Sans Mono";
    public static final int BASE_SIZE = 16;

    private EditorStyles() {
    }

    /** A key that prints its name, for debugging. */
    private record Key(String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** The paragraph attributes: {@code stored} is the style saved in the book, {@code shown} the one it looks like. */
    public static SimpleAttributeSet paragraph(ParaStyle shown, ParaStyle stored) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(STYLE, stored);
        StyleConstants.setFontFamily(a, SERIF);
        StyleConstants.setFontSize(a, BASE_SIZE);
        StyleConstants.setForeground(a, new Color(0x22, 0x22, 0x22));
        StyleConstants.setLineSpacing(a, 0.2f);
        StyleConstants.setSpaceBelow(a, 8);
        StyleConstants.setAlignment(a, StyleConstants.ALIGN_LEFT);
        switch (shown) {
            case TEXT -> {
            }
            case CHAPTER_TITLE -> {
                StyleConstants.setFontSize(a, 28);
                StyleConstants.setBold(a, true);
                StyleConstants.setForeground(a, HEADING_COLOUR);
                StyleConstants.setSpaceAbove(a, 6);
                StyleConstants.setSpaceBelow(a, 16);
            }
            case HEADING -> {
                StyleConstants.setFontFamily(a, SANS);
                StyleConstants.setFontSize(a, 20);
                StyleConstants.setBold(a, true);
                StyleConstants.setForeground(a, HEADING_COLOUR);
                StyleConstants.setSpaceAbove(a, 14);
                StyleConstants.setSpaceBelow(a, 6);
            }
            case SUBHEADING -> {
                StyleConstants.setFontFamily(a, SANS);
                StyleConstants.setFontSize(a, 17);
                StyleConstants.setBold(a, true);
                StyleConstants.setItalic(a, true);
                StyleConstants.setSpaceAbove(a, 10);
                StyleConstants.setSpaceBelow(a, 4);
            }
            case QUOTE -> {
                StyleConstants.setLeftIndent(a, 36);
                StyleConstants.setRightIndent(a, 36);
                StyleConstants.setForeground(a, new Color(0x44, 0x44, 0x44));
                StyleConstants.setSpaceBelow(a, 4);
            }
            case EPIGRAPH -> {
                StyleConstants.setItalic(a, true);
                StyleConstants.setFontSize(a, 14);
                StyleConstants.setAlignment(a, StyleConstants.ALIGN_RIGHT);
                StyleConstants.setLeftIndent(a, 160);
                StyleConstants.setForeground(a, new Color(0x44, 0x44, 0x44));
                StyleConstants.setSpaceBelow(a, 4);
            }
            case POEM -> {
                StyleConstants.setLeftIndent(a, 48);
                StyleConstants.setSpaceBelow(a, 0);
            }
            case LETTER -> {
                StyleConstants.setLeftIndent(a, 36);
                StyleConstants.setRightIndent(a, 36);
                StyleConstants.setItalic(a, true);
                StyleConstants.setSpaceBelow(a, 4);
            }
            case CODE -> {
                StyleConstants.setFontFamily(a, MONO);
                StyleConstants.setFontSize(a, 14);
                StyleConstants.setLeftIndent(a, 24);
                StyleConstants.setSpaceBelow(a, 0);
            }
            case CENTERED -> StyleConstants.setAlignment(a, StyleConstants.ALIGN_CENTER);
        }
        return a;
    }
}
