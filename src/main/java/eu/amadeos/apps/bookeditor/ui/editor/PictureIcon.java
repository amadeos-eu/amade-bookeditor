package eu.amadeos.apps.bookeditor.ui.editor;

import eu.amadeos.apps.bookeditor.domain.book.Block;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A picture in the text: scaled to the width its size asks for (of a 560 pixel text column), the caption under it,
 * and a small warning when it has no description yet.
 */
final class PictureIcon implements Icon {
    static final int COLUMN = 560;
    private static final Font CAPTION = new Font(EditorStyles.SERIF, Font.ITALIC, 13);
    private static final Font NOTE = new Font(EditorStyles.SANS, Font.BOLD, 11);
    private static final int CAPTION_HEIGHT = 24;

    private final Block.Picture picture;
    private final BufferedImage image;
    private final int width;
    private final int height;

    PictureIcon(Block.Picture picture, BufferedImage image) {
        this.picture = picture;
        this.image = image;
        width = COLUMN * picture.size().percent() / 100;
        height = image == null ? width / 2 : Math.max(16, (int) Math.round((double) image.getHeight() * width / image.getWidth()));
    }

    Block.Picture picture() {
        return picture;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int top = y + 6;
        if (image != null) {
            g.drawImage(image, x, top, width, height, null);
        } else {
            g.setColor(new Color(0xEE, 0xEE, 0xEE));
            g.fillRect(x, top, width, height);
            g.setColor(Color.GRAY);
            g.drawRect(x, top, width - 1, height - 1);
            g.drawString(picture.resource(), x + 8, top + 20);
        }
        if (picture.description().isEmpty()) {
            String warn = "!";
            g.setColor(new Color(0xE6, 0x8A, 0x00));
            g.fillOval(x + width - 26, top + 6, 20, 20);
            g.setColor(Color.WHITE);
            g.setFont(NOTE.deriveFont(14f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(warn, x + width - 16 - fm.stringWidth(warn) / 2, top + 21);
        }
        if (!picture.caption().isEmpty()) {
            g.setFont(CAPTION);
            g.setColor(new Color(0x55, 0x55, 0x55));
            FontMetrics fm = g.getFontMetrics();
            String caption = picture.caption();
            while (fm.stringWidth(caption) > width && caption.length() > 4) caption = caption.substring(0, caption.length() - 2);
            if (!caption.equals(picture.caption())) caption = caption.strip() + "…";
            g.drawString(caption, x + (width - fm.stringWidth(caption)) / 2, top + height + 17);
        }
        if (c != null && c.hasFocus() && c instanceof javax.swing.text.JTextComponent tc && selected(tc, x, y)) {
            g.setColor(new Color(0x00, 0x80, 0x80));
            g.setStroke(new BasicStroke(2));
            g.drawRect(x, top, width - 1, height - 1);
        }
        g.dispose();
    }

    /** Whether the text selection covers this picture. */
    private boolean selected(javax.swing.text.JTextComponent tc, int x, int y) {
        int pos = tc.viewToModel2D(new java.awt.Point(x + width / 2, y + height / 2));
        return pos >= tc.getSelectionStart() && pos < tc.getSelectionEnd();
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return 6 + height + (picture.caption().isEmpty() ? 6 : CAPTION_HEIGHT);
    }
}
