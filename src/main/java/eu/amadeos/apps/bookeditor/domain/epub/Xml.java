package eu.amadeos.apps.bookeditor.domain.epub;

/** Escaping for the XML the writer builds as text. */
final class Xml {
    private Xml() {
    }

    static String text(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    // Characters XML 1.0 does not allow (control characters, the object character of the editor)
                    if (c < 0x20 && c != '\n' && c != '\t' || c == '￼' || c == '￾' || c == '￿') continue;
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    static String attr(String s) {
        return text(s).replace("\n", "&#10;");
    }
}
