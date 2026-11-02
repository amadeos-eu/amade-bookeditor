package eu.amadeos.apps.bookeditor.ui.dialogs;

import javax.swing.JComboBox;
import java.util.List;
import java.util.Locale;

/** The languages a book can be written in, by their own names; a box that also takes other language tags. */
public final class BookLanguages {
    public record Choice(String code, String name) {
        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    public static final List<Choice> CHOICES = List.of(
            new Choice("en", "English"), new Choice("de", "Deutsch"), new Choice("fr", "Français"),
            new Choice("it", "Italiano"), new Choice("es", "Español"), new Choice("nl", "Nederlands"),
            new Choice("pt", "Português"), new Choice("pl", "Polski"), new Choice("cs", "Čeština"),
            new Choice("sv", "Svenska"), new Choice("da", "Dansk"), new Choice("nb", "Norsk bokmål"),
            new Choice("fi", "Suomi"), new Choice("hu", "Magyar"), new Choice("el", "Ελληνικά"),
            new Choice("ro", "Română"), new Choice("hr", "Hrvatski"), new Choice("sl", "Slovenščina"),
            new Choice("la", "Latina"));

    private BookLanguages() {
    }

    public static JComboBox<Object> box(String code) {
        JComboBox<Object> box = new JComboBox<>(CHOICES.toArray());
        box.setEditable(true);
        select(box, code);
        return box;
    }

    public static void select(JComboBox<Object> box, String code) {
        for (Choice c : CHOICES) {
            if (c.code.equalsIgnoreCase(code)) {
                box.setSelectedItem(c);
                return;
            }
        }
        box.setSelectedItem(code == null || code.isBlank() ? CHOICES.getFirst() : code);
    }

    /** The language tag chosen or typed ("de", "en-GB"); English for nothing usable. */
    public static String code(JComboBox<Object> box) {
        Object v = box.getEditor().getItem();
        if (v instanceof Choice c) return c.code;
        String text = v == null ? "" : v.toString().strip();
        for (Choice c : CHOICES) if (c.toString().equals(text) || c.name.equalsIgnoreCase(text)) return c.code;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([A-Za-z-]+)\\)").matcher(text);
        if (m.find()) text = m.group(1);
        return text.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*") ? text : "en";
    }

    /** The language of the program, if it is one of the choices, for a new book. */
    public static String defaultCode(Locale locale) {
        String lang = locale.getLanguage();
        return CHOICES.stream().anyMatch(c -> c.code.equals(lang)) ? lang : "en";
    }
}
