package eu.amadeos.apps.bookeditor.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {
    @Test
    void backupsKeepTheLastTenVersions(@TempDir Path dir) throws Exception {
        Path book = dir.resolve("Harbour Light.epub");
        Files.writeString(book, "x");
        Backups backups = new Backups(dir.resolve("backups"));
        for (int i = 0; i < 12; i++) backups.keep(book, LocalDateTime.of(2026, 11, 2, 19, 0, i));
        List<String> names;
        try (Stream<Path> s = Files.list(backups.folder())) {
            names = s.map(p -> p.getFileName().toString()).sorted().toList();
        }
        assertEquals(Backups.KEEP, names.size());
        assertEquals("Harbour Light 2026-11-02 19-00-02.epub", names.getFirst());
    }

    @Test
    void backupsOfOtherBooksAreLeftAlone(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("A.epub"), ab = dir.resolve("A B.epub");
        Files.writeString(a, "a");
        Files.writeString(ab, "ab");
        Backups backups = new Backups(dir.resolve("backups"));
        for (int i = 0; i < 3; i++) backups.keep(ab, LocalDateTime.of(2026, 1, 1, 0, 0, i));
        for (int i = 0; i < 12; i++) backups.keep(a, LocalDateTime.of(2026, 1, 2, 0, 0, i));
        try (Stream<Path> s = Files.list(backups.folder())) {
            assertEquals(13, s.count());
        }
    }

    @Test
    void preferencesRememberRecentBooksAndSettings(@TempDir Path dir) throws Exception {
        Path f1 = Files.writeString(dir.resolve("one.epub"), "1");
        Path f2 = Files.writeString(dir.resolve("two.epub"), "2");
        EditorPreferences p = new EditorPreferences(dir.resolve("book-editor.properties"));
        p.addRecent(f1);
        p.addRecent(f2);
        p.addRecent(f1);
        p.set("autosave", false);
        EditorPreferences again = new EditorPreferences(dir.resolve("book-editor.properties"));
        assertEquals(List.of(f1.toAbsolutePath(), f2.toAbsolutePath()), again.recentBooks());
        assertFalse(again.flag("autosave", true));
        assertTrue(again.flag("missing", true));
        Files.delete(f2);
        assertEquals(1, again.recentBooks().size());
    }
}
