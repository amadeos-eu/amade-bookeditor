package eu.amadeos.apps.bookeditor.domain.importer;

import eu.amadeos.apps.bookeditor.domain.book.Book;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/** Starts a book from a file: plain text, Markdown, or a word processor document. */
public final class BookImport {
    /** The file extensions a book can start from, the usual ones first. */
    public static final List<String> EXTENSIONS = List.of("odt", "docx", "doc", "rtf", "txt", "md", "markdown");

    /** The file needs LibreOffice to be read (.doc, .rtf), and it is not installed. */
    public static final class NeedsLibreOfficeException extends IOException {
        public NeedsLibreOfficeException() {
            super("This file needs LibreOffice, which is not installed");
        }
    }

    private BookImport() {
    }

    public static TextImport.Result read(Path file, Book book, Supplier<Optional<LibreOfficeConverter>> office) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        return switch (ext) {
            case "odt", "docx" -> OfficeImport.read(Files.readAllBytes(file), ext, book);
            case "doc", "rtf" -> {
                LibreOfficeConverter converter = office.get().orElseThrow(NeedsLibreOfficeException::new);
                yield OfficeImport.read(converter.toOdt(file), "odt", book);
            }
            default -> TextImport.read(file, book);
        };
    }
}
