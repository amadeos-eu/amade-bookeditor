package eu.amadeos.apps.bookeditor.ui.editor;

import eu.amadeos.apps.bookeditor.domain.book.Block;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Content the editor keeps but cannot edit (a table, a list, a formula): a grey box with what it is and the start
 * of its text.
 */
final class KeptIcon implements Icon {
    private static final Font LABEL = new Font(EditorStyles.SANS, Font.BOLD, 12);
    private static final Font HINT = new Font(EditorStyles.SANS, Font.PLAIN, 11);
    private static final Font PREVIEW = new Font(EditorStyles.SERIF, Font.PLAIN, 13);
    private static final int HEIGHT = 64;

    private final Block.Kept kept;
    private final String label;
    private final String hint;

    KeptIcon(Block.Kept kept, String label, String hint) {
        this.kept = kept;
        this.label = label;
        this.hint = hint;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int top = y + 4, w = getIconWidth(), h = HEIGHT - 8;
        g.setColor(new Color(0xEE, 0xEE, 0xEE));
        g.fillRoundRect(x, top, w - 1, h, 10, 10);
        g.setColor(new Color(0xB0, 0xB0, 0xB0));
        g.drawRoundRect(x, top, w - 1, h, 10, 10);
        g.setFont(LABEL);
        g.setColor(new Color(0x44, 0x44, 0x44));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + 12, top + 19);
        int after = x + 12 + fm.stringWidth(label) + 10;
        g.setFont(HINT);
        g.setColor(new Color(0x77, 0x77, 0x77));
        g.drawString(fit(g.getFontMetrics(), hint, x + w - 12 - after), after, top + 19);
        g.setFont(PREVIEW);
        g.setColor(new Color(0x55, 0x55, 0x55));
        g.drawString(fit(g.getFontMetrics(), kept.text(), w - 24), x + 12, top + 41);
        g.dispose();
    }

    private static String fit(FontMetrics fm, String text, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "…") > width) t = t.substring(0, t.length() - 1);
        return t.strip() + "…";
    }

    @Override
    public int getIconWidth() {
        return PictureIcon.COLUMN;
    }

    @Override
    public int getIconHeight() {
        return HEIGHT;
    }
}
