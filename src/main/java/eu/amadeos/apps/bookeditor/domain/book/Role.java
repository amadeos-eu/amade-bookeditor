package eu.amadeos.apps.bookeditor.domain.book;

/** What a person did for the book, with the MARC relator code EPUB uses. */
public enum Role {
    AUTHOR("aut"), ILLUSTRATOR("ill"), TRANSLATOR("trl"), EDITOR("edt"), PHOTOGRAPHER("pht"), NARRATOR("nrt");

    private final String code;

    Role(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Role of(String code) {
        for (Role r : values()) if (r.code.equals(code)) return r;
        return AUTHOR;
    }
}
