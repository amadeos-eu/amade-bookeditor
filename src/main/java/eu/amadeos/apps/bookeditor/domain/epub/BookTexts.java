package eu.amadeos.apps.bookeditor.domain.epub;

import java.util.Locale;
import java.util.Map;

/**
 * The few words the book itself contains besides the author's text ("Chapter 3", "Contents"), in the language
 * of the book, which need not be the language of the program.
 */
public record BookTexts(String chapter, String contents, String cover, String titlePage, String dedication,
                        String footnotes, String start) {
    private static final Map<String, BookTexts> ALL = Map.of(
            "en", new BookTexts("Chapter %d", "Contents", "Cover", "Title Page", "Dedication", "Notes", "Start"),
            "de", new BookTexts("Kapitel %d", "Inhalt", "Umschlag", "Titelseite", "Widmung", "Anmerkungen", "Beginn"),
            "fr", new BookTexts("Chapitre %d", "Table des matières", "Couverture", "Page de titre", "Dédicace",
                    "Notes", "Début"),
            "it", new BookTexts("Capitolo %d", "Indice", "Copertina", "Frontespizio", "Dedica", "Note", "Inizio"),
            "es", new BookTexts("Capítulo %d", "Índice", "Cubierta", "Portada", "Dedicatoria", "Notas", "Inicio"),
            "nl", new BookTexts("Hoofdstuk %d", "Inhoud", "Omslag", "Titelpagina", "Opdracht", "Noten", "Begin"),
            "pt", new BookTexts("Capítulo %d", "Índice", "Capa", "Folha de rosto", "Dedicatória", "Notas", "Início"));

    /** The texts for a language tag such as "de" or "en-GB"; English for languages not known here. */
    public static BookTexts of(String languageTag) {
        String lang = languageTag == null ? "" : languageTag.toLowerCase(Locale.ROOT).split("[-_]")[0];
        return ALL.getOrDefault(lang, ALL.get("en"));
    }

    public String chapter(int number) {
        return chapter.formatted(number);
    }
}
