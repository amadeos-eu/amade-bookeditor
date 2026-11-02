package eu.amadeos.apps.bookeditor.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.bookeditor.domain.check.EpubCheckRunner;
import eu.amadeos.apps.bookeditor.repository.Backups;
import eu.amadeos.apps.bookeditor.repository.EditorPreferences;
import eu.amadeos.apps.bookeditor.ui.main.MainWindow;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.typeset.Hyphenators;

import java.util.Locale;

/**
 * amade-bookeditor bindings. Theme, language, folders and the background executor come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class BookEditorModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.bookeditor.messages";

    @Override
    protected void configure() {
        bind(EditorPreferences.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
    }

    /** The hyphenation patterns of the Debian {@code hyphen-*} packages, for the preview. */
    @Provides
    @Singleton
    Hyphenators hyphenators() {
        return new Hyphenators(Hyphenators.SYSTEM);
    }

    /** EPUBCheck from the Debian package {@code epubcheck}, if it is installed. */
    @Provides
    @Singleton
    EpubCheckRunner epubcheck() {
        return new EpubCheckRunner(CommandRunner.system(), EpubCheckRunner.DEBIAN_JAR);
    }

    /** The last ten versions of every book, in {@code ~/.cache/book-editor/backups}. */
    @Provides
    @Singleton
    Backups backups(AppDirectories directories) {
        return new Backups(directories.cache().resolve("backups"));
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(BookEditorModule.class, MESSAGES, locale);
    }
}
