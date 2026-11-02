package eu.amadeos.apps.bookeditor.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Settings of the editor in {@code ~/.config/amadeos/book-editor/book-editor.properties}: the books opened last,
 * the folder of the last file dialog and the choices of the View menu. Each change is written at once.
 */
public class EditorPreferences {
    public static final String FILE = "book-editor.properties";
    private static final int RECENT = 8;

    private final Path file;
    private final Properties properties;

    @Inject
    public EditorPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public EditorPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public List<Path> recentBooks() {
        List<Path> list = new ArrayList<>();
        for (int i = 0; i < RECENT; i++) {
            String v = properties.getProperty("recent." + i, "");
            if (!v.isEmpty() && Files.isRegularFile(Path.of(v))) list.add(Path.of(v));
        }
        return list;
    }

    public void addRecent(Path book) {
        List<Path> list = new ArrayList<>(recentBooks());
        Path abs = book.toAbsolutePath().normalize();
        list.remove(abs);
        list.addFirst(abs);
        for (int i = 0; i < RECENT; i++) {
            if (i < list.size()) properties.setProperty("recent." + i, list.get(i).toString());
            else properties.remove("recent." + i);
        }
        save();
    }

    public String get(String key, String fallback) {
        return properties.getProperty(key, fallback);
    }

    public boolean flag(String key, boolean fallback) {
        String v = properties.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    public void set(String key, Object value) {
        properties.setProperty(key, String.valueOf(value));
        save();
    }

    private void save() {
        PropertiesFile.store(file, properties, "amadeOS Book Editor");
    }
}
