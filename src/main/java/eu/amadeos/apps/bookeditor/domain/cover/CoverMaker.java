package eu.amadeos.apps.bookeditor.domain.cover;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a cover from a {@link CoverDesign}: 1600 × 2400 pixels (the 2:3 shape shops ask for), the picture filling
 * the cover, title and author large enough to read as a small thumbnail.
 */
public final class CoverMaker {
    public static final int WIDTH = 1600;
    public static final int HEIGHT = 2400;

    private CoverMaker() {
    }

    // --8<-- [start:draw]
    public static BufferedImage draw(CoverDesign d, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(d.background());
        g.fillRect(0, 0, width, height);
        boolean onPicture = d.picture() != null;
        if (onPicture) {
            // Fill the cover, cutting off what sticks out on one side
            BufferedImage p = d.picture();
            double scale = Math.max((double) width / p.getWidth(), (double) height / p.getHeight());
            int w = (int) Math.ceil(p.getWidth() * scale), h = (int) Math.ceil(p.getHeight() * scale);
            g.drawImage(p, (width - w) / 2, (height - h) / 2, w, h, null);
        }
        Color ink = onPicture || dark(d.background()) ? Color.WHITE : new Color(0x1A, 0x1A, 0x1A);
        double unit = width / 100.0;
        int margin = (int) (8 * unit);
        int textWidth = width - 2 * margin;

        Font titleFont = new Font(d.typeface().family(), Font.BOLD, (int) (11 * unit));
        List<String> titleLines = fit(g, d.title().toUpperCase(java.util.Locale.ROOT), titleFont, textWidth, 4);
        titleFont = g.getFont();
        FontMetrics tm = g.getFontMetrics(titleFont);
        Font subFont = new Font(d.typeface().family(), Font.PLAIN, (int) (4.5 * unit));
        List<String> subLines = d.subtitle().isEmpty() ? List.of() : fit(g, d.subtitle(), subFont, textWidth, 2);
        subFont = g.getFont();
        FontMetrics sm = g.getFontMetrics(subFont);
        int titleBlock = titleLines.size() * tm.getHeight() + (subLines.isEmpty() ? 0 : (int) (2 * unit) + subLines.size() * sm.getHeight());
        int top = switch (d.placement()) {
            case TOP -> (int) (12 * unit);
            case MIDDLE -> (height - titleBlock) / 2;
            case BOTTOM -> height - titleBlock - (int) (34 * unit);
        };
        if (onPicture) {
            // A soft dark band behind the title, and one behind the author, so text stays readable on any picture
            int pad = (int) (5 * unit);
            band(g, top - pad, titleBlock + 2 * pad, width);
            band(g, height - (int) (20 * unit), (int) (20 * unit), width);
        }
        g.setColor(ink);
        int y = top;
        for (String line : titleLines) {
            g.setFont(titleFont);
            y += tm.getAscent();
            g.drawString(line, (width - tm.stringWidth(line)) / 2, y);
            y += tm.getDescent() + tm.getLeading();
        }
        if (!subLines.isEmpty()) {
            y += (int) (2 * unit);
            g.setFont(subFont);
            for (String line : subLines) {
                y += sm.getAscent();
                g.drawString(line, (width - sm.stringWidth(line)) / 2, y);
                y += sm.getDescent() + sm.getLeading();
            }
        }
        if (!d.author().isEmpty()) {
            Font authorFont = new Font(d.typeface().family(), Font.PLAIN, (int) (5.5 * unit));
            List<String> lines = fit(g, d.author(), authorFont, textWidth, 2);
            FontMetrics am = g.getFontMetrics();
            int ay = height - (int) (8 * unit) - lines.size() * am.getHeight();
            for (String line : lines) {
                ay += am.getAscent();
                g.drawString(line, (width - am.stringWidth(line)) / 2, ay);
                ay += am.getDescent() + am.getLeading();
            }
        }
        g.dispose();
        return img;
    }
    // --8<-- [end:draw]

    private static void band(Graphics2D g, int y, int h, int width) {
        int fade = h / 3;
        g.setPaint(new GradientPaint(0, y - fade, new Color(0, 0, 0, 0), 0, y, new Color(0, 0, 0, 120)));
        g.fillRect(0, y - fade, width, fade);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, y, width, h);
        g.setPaint(new GradientPaint(0, y + h, new Color(0, 0, 0, 120), 0, y + h + fade, new Color(0, 0, 0, 0)));
        g.fillRect(0, y + h, width, fade);
    }

    static boolean dark(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() < 140;
    }

    /**
     * Breaks the text into at most {@code maxLines} lines of {@code width}, making the font smaller until it fits;
     * leaves the font that fits set on {@code g}.
     */
    static List<String> fit(Graphics2D g, String text, Font font, int width, int maxLines) {
        Font f = font;
        while (true) {
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = wrap(fm, text, width);
            boolean fits = lines.size() <= maxLines && lines.stream().allMatch(l -> fm.stringWidth(l) <= width);
            if (fits || f.getSize() <= 12) return lines;
            f = f.deriveFont(f.getSize2D() * 0.9f);
        }
    }

    static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            String next = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(next) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** The cover as a JPEG for the book. */
    public static byte[] jpeg(BufferedImage image) throws IOException {
        return Pictures.jpeg(image, 0.9f);
    }
}
