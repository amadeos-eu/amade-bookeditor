package eu.amadeos.apps.bookeditor.domain.book;

/** What a {@link Block.Kept} holds; each has its label in the texts ({@code kept.<key>}). */
public enum KeptKind {
    TABLE("table"), LIST("list"), FORMULA("formula"), DRAWING("drawing"), OTHER("other");

    private final String key;

    KeptKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static KeptKind of(String key) {
        for (KeptKind k : values()) if (k.key.equals(key)) return k;
        return OTHER;
    }
}
