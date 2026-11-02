package eu.amadeos.apps.bookeditor.domain.book;

/** How wide a picture is shown, as a share of the reading area; readers keep the proportions. */
public enum PictureSize {
    SMALL("small", 40), MEDIUM("medium", 70), FULL("full", 100);

    private final String key;
    private final int percent;

    PictureSize(String key, int percent) {
        this.key = key;
        this.percent = percent;
    }

    public String key() {
        return key;
    }

    public int percent() {
        return percent;
    }

    public static PictureSize of(String key) {
        for (PictureSize s : values()) if (s.key.equals(key)) return s;
        return MEDIUM;
    }
}
