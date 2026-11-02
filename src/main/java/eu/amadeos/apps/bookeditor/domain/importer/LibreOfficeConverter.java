package eu.amadeos.apps.bookeditor.domain.importer;

import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * LibreOffice as a converter for the word processor formats the editor does not read itself (.doc, .rtf): it
 * turns them into OpenDocument. Each conversion has a profile of its own in a temporary folder, so it works
 * while LibreOffice is open and leaves the user's settings alone.
 */
public final class LibreOfficeConverter {
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final Path soffice;
    private final CommandRunner runner;

    public LibreOfficeConverter(Path soffice, CommandRunner runner) {
        this.soffice = soffice;
        this.runner = runner;
    }

    /** LibreOffice as installed: {@code soffice} or {@code libreoffice} on the path, or in its Windows folder. */
    public static Optional<LibreOfficeConverter> find() {
        List<Path> candidates = new ArrayList<>();
        String path = System.getenv("PATH");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.isBlank()) continue;
                for (String name : windows ? List.of("soffice.exe", "soffice.com") : List.of("soffice", "libreoffice")) {
                    candidates.add(Path.of(dir, name));
                }
            }
        }
        if (windows) {
            for (String env : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String dir = System.getenv(env);
                if (dir != null) candidates.add(Path.of(dir, "LibreOffice", "program", "soffice.exe"));
            }
        }
        return candidates.stream().filter(p -> Files.isRegularFile(p) && Files.isExecutable(p)).findFirst()
                .map(p -> new LibreOfficeConverter(p, CommandRunner.system()));
    }

    /** The file as OpenDocument Text. */
    public byte[] toOdt(Path file) throws IOException {
        Path work = Files.createTempDirectory("amade-bookeditor-convert");
        try {
            String name = file.getFileName().toString();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "doc";
            Path in = work.resolve("document." + ext);
            Files.copy(file, in);
            Path out = Files.createDirectories(work.resolve("out"));
            List<String> command = List.of(soffice.toString(), "-env:UserInstallation=" + work.resolve("profile").toUri(),
                    "--headless", "--norestore", "--nologo", "--convert-to", "odt", "--outdir", out.toString(), in.toString());
            ProgramResult run;
            try {
                run = runner.run(command, TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while LibreOffice converted the document", e);
            }
            Path result = out.resolve("document.odt");
            if (!run.succeeded() || !Files.isRegularFile(result)) {
                String log = (run.stderrText(Charset.defaultCharset()) + "\n" + run.stdoutText(Charset.defaultCharset())).strip();
                throw new IOException("LibreOffice could not convert the document" + (log.isEmpty() ? "" : ": " + log));
            }
            return Files.readAllBytes(result);
        } finally {
            try (Stream<Path> paths = Files.walk(work)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // a temporary folder left behind
            }
        }
    }
}
