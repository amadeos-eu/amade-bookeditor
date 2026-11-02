package eu.amadeos.apps.bookeditor.domain.book;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The ready-made designs of a book: each one is a style sheet the book carries ({@code styles/book.css}). */
public enum Look {
    CLASSIC("classic"), MODERN("modern"), CHILDREN("children"), POETRY("poetry");

    private final String key;

    Look(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The style sheet of this look. */
    public String css() {
        try (InputStream in = Look.class.getResourceAsStream("/eu/amadeos/apps/bookeditor/looks/" + key + ".css")) {
            if (in == null) throw new IllegalStateException("missing style sheet of look " + key);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Look of(String key) {
        for (Look l : values()) if (l.key.equals(key)) return l;
        return CLASSIC;
    }
}
