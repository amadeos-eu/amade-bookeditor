package eu.amadeos.apps.bookeditor.domain.book;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a book says about itself (the OPF metadata), as the Book Details dialog shows it.
 *
 * @param identifier a {@code urn:uuid:} made once for the book, so readers know an update is the same book
 * @param isbn       optional, digits and hyphens as typed
 * @param published  empty while not set
 * @param keywords   subjects, comma separated in the dialog
 * @param summary    the accessibility summary; empty makes one from the book's content
 */
public record BookInfo(String identifier, String title, String subtitle, List<Person> people, String language,
                       String series, int volume, String publisher, String published, String isbn,
                       List<String> keywords, String description, License license, String summary) {
    public BookInfo {
        Objects.requireNonNull(identifier, "identifier");
        title = clean(title);
        subtitle = clean(subtitle);
        people = people.stream().filter(p -> !p.name().isEmpty()).toList();
        language = clean(language).isEmpty() ? "en" : clean(language);
        series = clean(series);
        volume = Math.max(0, volume);
        publisher = clean(publisher);
        published = clean(published);
        isbn = clean(isbn);
        keywords = keywords.stream().map(String::strip).filter(k -> !k.isEmpty()).toList();
        description = description == null ? "" : description.strip();
        license = license == null ? License.ALL_RIGHTS_RESERVED : license;
        summary = summary == null ? "" : summary.strip();
    }

    public static BookInfo create(String title, String author, String language) {
        return new BookInfo("urn:uuid:" + UUID.randomUUID(), title, "",
                author == null || author.isBlank() ? List.of() : List.of(new Person(author, Role.AUTHOR)), language,
                "", 0, "", LocalDate.now().toString(), "", List.of(), "", License.ALL_RIGHTS_RESERVED, "");
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    public List<Person> authors() {
        return people.stream().filter(p -> p.role() == Role.AUTHOR).toList();
    }

    /** "Anna Berger, Tom Weiss": the authors, or everybody when nobody is called author. */
    public String authorLine() {
        List<Person> list = authors().isEmpty() ? people : authors();
        return String.join(", ", list.stream().map(Person::name).toList());
    }

    public BookInfo withTitle(String newTitle) {
        return new BookInfo(identifier, newTitle, subtitle, people, language, series, volume, publisher, published,
                isbn, keywords, description, license, summary);
    }

    public BookInfo withDescription(String text) {
        return new BookInfo(identifier, title, subtitle, people, language, series, volume, publisher, published,
                isbn, keywords, text, license, summary);
    }
}
