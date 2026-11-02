package eu.amadeos.apps.bookeditor.domain.book;

/**
 * The paragraph styles of the editor. Each one stands for exactly one HTML element (or one class of it), so a
 * book always becomes clean EPUB and can be read back without losing anything.
 */
public enum ParaStyle {
    /** Body text, {@code <p>}. */
    TEXT("text"),
    /** The title of the chapter, {@code <h1>}; the first paragraph of a chapter. */
    CHAPTER_TITLE("chapter-title"),
    /** A heading inside a chapter, {@code <h2>}. */
    HEADING("heading"),
    /** A smaller heading, {@code <h3>}. */
    SUBHEADING("subheading"),
    /** Quoted text, {@code <blockquote>}. */
    QUOTE("quote"),
    /** A motto at the start of a chapter, {@code <blockquote class="epigraph">}. */
    EPIGRAPH("epigraph"),
    /** One line of a poem; lines stay as written, an empty line starts a new stanza. */
    POEM("poem"),
    /** A letter or note inside the story, set apart. */
    LETTER("letter"),
    /** Text set in a fixed-width font, {@code <pre>}. */
    CODE("code"),
    /** Centred text, for example "The End". */
    CENTERED("centered");

    private final String key;

    ParaStyle(String key) {
        this.key = key;
    }

    /** The name in the texts ({@code style.<key>}) and in the editor's own files. */
    public String key() {
        return key;
    }

    public boolean heading() {
        return this == CHAPTER_TITLE || this == HEADING || this == SUBHEADING;
    }

    /** Styles whose paragraphs a following Enter continues; after the others Enter goes back to text. */
    public boolean continues() {
        return this == TEXT || this == QUOTE || this == POEM || this == LETTER || this == CODE;
    }

    public static ParaStyle of(String key) {
        for (ParaStyle s : values()) if (s.key.equals(key)) return s;
        return TEXT;
    }
}
