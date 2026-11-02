package eu.amadeos.apps.bookeditor.domain.book;

/** The licences a book can say it is under; the text of the rights line comes from {@link #rights()}. */
public enum License {
    ALL_RIGHTS_RESERVED("all-rights-reserved", "All rights reserved", ""),
    CC_BY("cc-by", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    CC_BY_SA("cc-by-sa", "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
    CC_BY_NC("cc-by-nc", "CC BY-NC 4.0", "https://creativecommons.org/licenses/by-nc/4.0/"),
    CC_BY_NC_ND("cc-by-nc-nd", "CC BY-NC-ND 4.0", "https://creativecommons.org/licenses/by-nc-nd/4.0/"),
    CC0("cc0", "CC0 1.0 (public domain)", "https://creativecommons.org/publicdomain/zero/1.0/"),
    PUBLIC_DOMAIN("public-domain", "Public domain", "");

    private final String key;
    private final String label;
    private final String url;

    License(String key, String label, String url) {
        this.key = key;
        this.label = label;
        this.url = url;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String url() {
        return url;
    }

    /** The {@code dc:rights} line: "© 2026 Anna Berger. CC BY 4.0 https://…". */
    public String rights(String year, String holder) {
        String owner = holder.isBlank() ? "" : "© " + year + " " + holder + ". ";
        return switch (this) {
            case PUBLIC_DOMAIN -> label;
            case CC0 -> label + " " + url;
            default -> (owner + label + (url.isEmpty() ? "" : " " + url)).strip();
        };
    }

    public static License of(String key) {
        for (License l : values()) if (l.key.equals(key)) return l;
        return ALL_RIGHTS_RESERVED;
    }

    /** The licence a rights line names, all rights reserved if none. */
    public static License fromRights(String rights) {
        if (rights == null) return ALL_RIGHTS_RESERVED;
        for (License l : values()) {
            if (!l.url.isEmpty() && rights.contains(l.url)) return l;
        }
        return rights.strip().equals(PUBLIC_DOMAIN.label) ? PUBLIC_DOMAIN : ALL_RIGHTS_RESERVED;
    }
}
