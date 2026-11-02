package eu.amadeos.apps.bookeditor.domain.cover;

import eu.amadeos.apps.bookeditor.domain.book.Resource;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Reading pictures for the book and making big ones smaller. */
public final class Pictures {
    /** The longest side of a picture after "Make smaller": sharp on tablets, small enough for any reader. */
    public static final int MAX_SIDE = 1600;

    private Pictures() {
    }

    public static Optional<BufferedImage> read(byte[] data) {
        try {
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(data)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * A smaller copy of a picture: at most {@link #MAX_SIDE} pixels on the longer side, in the format it had (JPEG
     * or PNG), so its name stays. Empty for other formats, or if it cannot be read or would not get smaller.
     */
    public static Optional<Resource> shrink(Resource r) {
        boolean jpeg = r.mediaType().equals("image/jpeg");
        if (!jpeg && !r.mediaType().equals("image/png")) return Optional.empty();
        Optional<BufferedImage> read = read(r.data());
        if (read.isEmpty()) return Optional.empty();
        BufferedImage img = read.get();
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(img.getWidth(), img.getHeight()));
        int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
        boolean alpha = !jpeg && img.getColorModel().hasAlpha();
        BufferedImage out = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        try {
            byte[] data = jpeg ? jpeg(out, 0.85f) : png(out);
            if (data.length >= r.size()) return Optional.empty();
            return Optional.of(new Resource(r.name(), r.mediaType(), data));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
