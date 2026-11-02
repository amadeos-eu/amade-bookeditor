package eu.amadeos.apps.bookeditor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {
    private static Properties load(String suffix) throws IOException {
        Properties p = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream("/eu/amadeos/apps/bookeditor/messages" + suffix + ".properties")) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void everyLanguageHasEveryText() throws IOException {
        Set<String> english = new TreeSet<>(load("").stringPropertyNames());
        for (String lang : new String[]{"_de", "_fr", "_it", "_es"}) {
            assertEquals(english, new TreeSet<>(load(lang).stringPropertyNames()), lang);
        }
    }

    @Test
    void everyPatternCanBeFormatted() throws IOException {
        for (String lang : new String[]{"", "_de", "_fr", "_it", "_es"}) {
            Properties p = load(lang);
            for (String key : p.stringPropertyNames()) {
                String v = p.getProperty(key);
                if (!v.contains("{")) continue;
                String done = new java.text.MessageFormat(v.replace("'", "''"), java.util.Locale.ENGLISH)
                        .format(new Object[]{1, 2, 3, 4, 5});
                assertTrue(!done.contains("{"), lang + " " + key + ": " + done);
            }
        }
    }

    @Test
    void everyTextTheCodeAsksForExists() throws IOException {
        Properties english = load("");
        Pattern used = Pattern.compile("msg\\.(?:get|format)\\(\"([a-z0-9.-]+[a-z0-9])\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = used.matcher(Files.readString(f));
                while (m.find()) if (!english.containsKey(m.group(1))) missing.add(m.group(1));
            }
        }
        assertTrue(missing.isEmpty(), missing.toString());
    }
}
