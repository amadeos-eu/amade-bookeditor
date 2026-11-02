package eu.amadeos.apps.bookeditor.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps the earlier versions of a book: before the editor overwrites a file for the first time after opening it,
 * the old file is copied to {@code ~/.local/share/amadeos/book-editor/backups}. The newest ten per book are kept.
 */
public final class Backups {
    public static final int KEEP = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");

    private final Path folder;

    public Backups(Path folder) {
        this.folder = folder;
    }

    public Path folder() {
        return folder;
    }

    /** Copies {@code book} into the backup folder as "Name 2026-11-02 19-40-12.epub". */
    public Path keep(Path book, LocalDateTime now) throws IOException {
        Files.createDirectories(folder);
        String name = book.getFileName().toString();
        String base = name.toLowerCase(java.util.Locale.ROOT).endsWith(".epub") ? name.substring(0, name.length() - 5) : name;
        Path copy = folder.resolve(base + " " + STAMP.format(now) + ".epub");
        Files.copy(book, copy, StandardCopyOption.REPLACE_EXISTING);
        prune(base);
        return copy;
    }

    private void prune(String base) throws IOException {
        List<Path> copies;
        try (Stream<Path> files = Files.list(folder)) {
            copies = files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(base + " ") && n.endsWith(".epub")
                        && n.substring(base.length() + 1, n.length() - 5).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}-\\d{2}");
            }).sorted().toList();
        }
        for (int i = 0; i < copies.size() - KEEP; i++) Files.deleteIfExists(copies.get(i));
    }
}
