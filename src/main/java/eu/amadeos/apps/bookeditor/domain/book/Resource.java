package eu.amadeos.apps.bookeditor.domain.book;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * A file inside the book that is not text: a picture or the cover.
 *
 * @param name      the file name inside the book's {@code images} folder, unique in the book
 * @param mediaType {@code image/jpeg}, {@code image/png}, {@code image/gif} or {@code image/svg+xml}
 */
public record Resource(String name, String mediaType, byte[] data) {
    public Resource {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mediaType, "mediaType");
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public int size() {
        return data.length;
    }

    /** The media type EPUB allows for a file name, empty for types it does not. */
    public static String mediaTypeOf(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".webp")) return "image/webp";
        return "";
    }

    /** A file name made of plain letters, digits, dots and hyphens, as some readers trip over others. */
    public static String safeName(String fileName) {
        String n = java.text.Normalizer.normalize(fileName, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]+", "-")
                .replaceAll("-+", "-").replaceAll("^-|-$", "");
        return n.isEmpty() || n.startsWith(".") ? "picture" + n : n;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Resource r && r.name.equals(name) && r.mediaType.equals(mediaType) && Arrays.equals(r.data, data);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "Resource[" + name + ", " + mediaType + ", " + data.length + " bytes]";
    }
}
