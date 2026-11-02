package eu.amadeos.apps.bookeditor.ui.editor;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A footnote in the text: its number, raised and in the note colour, the way a reader shows it. */
final class FootnoteIcon implements Icon {
    private static final Font FONT = new Font(EditorStyles.SANS, Font.BOLD, 11);
    private int number = 1;

    void setNumber(int n) {
        number = n;
    }

    int number() {
        return number;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(FONT);
        g.setColor(EditorStyles.NOTE_COLOUR);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(String.valueOf(number), x + 1, y + fm.getAscent());
        g.dispose();
    }

    @Override
    public int getIconWidth() {
        return 4 + String.valueOf(number).length() * 7;
    }

    @Override
    public int getIconHeight() {
        // Taller than the text, so the number sits above the line like a superscript
        return 22;
    }
}
