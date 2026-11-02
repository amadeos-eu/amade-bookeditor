package eu.amadeos.apps.bookeditor.domain.book;

/** Counts words the way writers do: runs of letters or digits, an apostrophe or hyphen inside keeps a word whole. */
public final class Words {
    /** Words on a printed page of a novel; gives the "about N pages" in the status bar. */
    public static final int PER_PAGE = 250;

    private Words() {
    }

    public static int count(String text) {
        int n = 0;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean word = Character.isLetterOrDigit(c)
                    || in && (c == '\'' || c == '’' || c == '-') && i + 1 < text.length()
                    && Character.isLetterOrDigit(text.charAt(i + 1));
            if (word && !in) n++;
            in = word;
        }
        return n;
    }

    public static int pages(int words) {
        return Math.max(1, (words + PER_PAGE / 2) / PER_PAGE);
    }
}
