package eu.amadeos.apps.bookeditor.bootstrap;

import eu.amadeos.apps.bookeditor.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.util.List;

public final class AmadeBookEditorApplication {
    public static final AppInfo APP = new AppInfo("amade-bookeditor", "Book Editor", "book-editor");

    private AmadeBookEditorApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeBookEditorApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-bookeditor " + version());
            return;
        }
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            window.setVisible(true);
            window.start(List.of(args));
        }, new BookEditorModule());
    }
    // --8<-- [end:main]
}
