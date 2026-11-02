package eu.amadeos.apps.bookeditor.ui.editor;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A scene break: three stars on their own line. */
final class SceneBreakIcon implements Icon {
    private static final Font FONT = new Font(EditorStyles.SERIF, Font.PLAIN, 18);
    private static final String STARS = "*     *     *";

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(FONT);
        g.setColor(new Color(0x55, 0x55, 0x55));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(STARS, x + (getIconWidth() - fm.stringWidth(STARS)) / 2, y + 26);
        g.dispose();
    }

    @Override
    public int getIconWidth() {
        return 160;
    }

    @Override
    public int getIconHeight() {
        return 36;
    }
}
