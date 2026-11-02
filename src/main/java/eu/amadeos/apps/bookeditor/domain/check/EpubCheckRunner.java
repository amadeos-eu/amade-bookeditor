package eu.amadeos.apps.bookeditor.domain.check;

import eu.amadeos.commons.json.Json;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Runs EPUBCheck, the W3C's checker for EPUB files (Debian package {@code epubcheck}), on a saved book, with its
 * messages in the language of the program where it has them.
 */
public final class EpubCheckRunner {
    public static final Path DEBIAN_JAR = Path.of("/usr/share/java/epubcheck.jar");

    /** What EPUBCheck said: its version and the messages; unavailable when it is not installed. */
    public record Result(boolean available, String version, List<Finding> findings) {
        public static final Result UNAVAILABLE = new Result(false, "", List.of());
    }

    private final CommandRunner runner;
    private final Path jar;

    public EpubCheckRunner(CommandRunner runner, Path jar) {
        this.runner = runner;
        this.jar = jar;
    }

    public boolean available() {
        return Files.isRegularFile(jar);
    }

    // --8<-- [start:run]
    public Result check(Path book, Locale locale) throws IOException, InterruptedException {
        if (!available()) return Result.UNAVAILABLE;
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = List.of(java, "-jar", jar.toString(), book.toString(), "--json", "-", "-q",
                "--locale", locale.toLanguageTag());
        ProgramResult result = runner.run(command, Duration.ofMinutes(3));
        return parse(result.stdoutText());
    }
    // --8<-- [end:run]

    /** Reads EPUBCheck's JSON report; usage and info messages are left out. */
    static Result parse(String json) throws IOException {
        int start = json.indexOf('{');
        if (start < 0) throw new IOException("EPUBCheck gave no report");
        Object report = Json.parse(json.substring(start));
        String version = Optional.ofNullable(Json.str(Json.member(report, "checker"), "checkerVersion")).orElse("");
        List<Finding> findings = new ArrayList<>();
        for (Object m : Json.list(report, "messages")) {
            String severity = Json.str(m, "severity");
            Finding.Kind kind = switch (severity == null ? "" : severity) {
                case "FATAL", "ERROR" -> Finding.Kind.EPUBCHECK_ERROR;
                case "WARNING" -> Finding.Kind.EPUBCHECK_WARNING;
                default -> null;
            };
            if (kind == null) continue;
            String where = "";
            List<Object> locations = Json.list(m, "locations");
            if (!locations.isEmpty()) {
                Object l = locations.getFirst();
                where = Json.str(l, "path");
                int line = Json.integer(l, "line");
                if (line > 0) where += ":" + line;
            }
            findings.add(new Finding(kind, "", Json.str(m, "ID") + ": " + Json.str(m, "message")
                    + (where == null || where.isEmpty() ? "" : " (" + where + ")")));
        }
        return new Result(true, version, findings);
    }
}
