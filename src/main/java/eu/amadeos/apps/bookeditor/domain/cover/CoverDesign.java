package eu.amadeos.apps.bookeditor.domain.cover;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * What the cover maker puts on a cover: the texts, a colour or a picture behind them, where the title sits and
 * which typeface it uses.
 *
 * @param picture the background picture, null for a plain colour
 */
public record CoverDesign(String title, String subtitle, String author, Color background, BufferedImage picture,
                          Placement placement, Typeface typeface) {
    public CoverDesign {
        title = title == null ? "" : title.strip();
        subtitle = subtitle == null ? "" : subtitle.strip();
        author = author == null ? "" : author.strip();
        Objects.requireNonNull(background, "background");
        placement = placement == null ? Placement.TOP : placement;
        typeface = typeface == null ? Typeface.SERIF : typeface;
    }

    /** The colours offered: deep blue, dark red, forest green, warm sand, slate and black. */
    public static final Color[] COLOURS = {
            new Color(0x1B, 0x26, 0x3B), new Color(0x7A, 0x1F, 0x2B), new Color(0x2E, 0x5E, 0x3E),
            new Color(0xD9, 0xC5, 0x9A), new Color(0x4A, 0x55, 0x68), new Color(0x15, 0x15, 0x15)};

    public enum Placement {
        TOP, MIDDLE, BOTTOM;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Typeface {
        SERIF("DejaVu Serif"), SANS("DejaVu Sans"), CONDENSED("DejaVu Sans Condensed");

        private final String family;

        Typeface(String family) {
            this.family = family;
        }

        public String family() {
            return family;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public CoverDesign withPicture(BufferedImage newPicture) {
        return new CoverDesign(title, subtitle, author, background, newPicture, placement, typeface);
    }
}
