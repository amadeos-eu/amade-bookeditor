package eu.amadeos.apps.bookeditor.tools;

import eu.amadeos.apps.bookeditor.bootstrap.BookEditorModule;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.License;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.Person;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Role;
import eu.amadeos.apps.bookeditor.domain.check.EpubCheckRunner;
import eu.amadeos.apps.bookeditor.domain.cover.CoverDesign;
import eu.amadeos.apps.bookeditor.domain.cover.CoverMaker;
import eu.amadeos.apps.bookeditor.domain.epub.EpubWriter;
import eu.amadeos.apps.bookeditor.repository.Backups;
import eu.amadeos.apps.bookeditor.repository.EditorPreferences;
import eu.amadeos.apps.bookeditor.ui.main.MainWindow;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.commons.typeset.Hyphenators;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Makes the pictures in docs/screenshots and for the handbooks: the editor with a sample book, the preview and
 * Check Book. Run under a display: {@code xvfb-run ./gradlew screenshots}.
 */
public final class Screenshots {
    private Screenshots() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/screenshots");
        Locale locale = args.length > 1 ? Locale.forLanguageTag(args[1]) : Locale.ENGLISH;
        Locale.setDefault(locale);
        Files.createDirectories(out);
        Path work = Files.createTempDirectory("book-editor-shots");
        Path book = work.resolve("Harbour Light.epub");
        new EpubWriter(sample(), Instant.now(), "dev").save(book);

        MainWindow[] holder = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            AmadeUI.install(ThemeStore.system().current());
            MainWindow w = new MainWindow(Messages.load(BookEditorModule.class, BookEditorModule.MESSAGES, locale), null,
                    new EditorPreferences(work.resolve("prefs.properties")), new Backups(work.resolve("backups")),
                    new EpubCheckRunner(CommandRunner.system(), EpubCheckRunner.DEBIAN_JAR),
                    new Hyphenators(Hyphenators.SYSTEM), new DesktopLauncher(), Executors.newSingleThreadExecutor(), null);
            w.setSize(1180, 720);
            w.setVisible(true);
            w.openFile(book);
            w.outline().select(eu.amadeos.apps.bookeditor.ui.main.OutlinePanel.Kind.CHAPTER,
                    w.book().chapters().get(1).id());
            holder[0] = w;
        });
        MainWindow w = holder[0];
        Thread.sleep(1500);
        shoot(w, out.resolve("amade-bookeditor.png"));
        SwingUtilities.invokeAndWait(() -> {
            w.setSize(1280, 720);
            togglePreview(w);
        });
        Thread.sleep(3000);
        shoot(w, out.resolve("amade-bookeditor-preview.png"));
        SwingUtilities.invokeAndWait(() -> {
            togglePreview(w);
            w.setSize(1180, 720);
            // two things to look at: a picture without description and no description of the book
            Book b = w.book();
            b.setInfo(b.info().withDescription(""));
            Chapter letter = b.chapters().get(3);
            List<Block> blocks = new ArrayList<>(letter.blocks());
            blocks.add(new Block.Picture(b.pictures().getFirst().getValue().resource(), "", "", PictureSize.SMALL));
            b.replace(letter.withBlocks(blocks));
            invoke(w, "checkBook");
        });
        Thread.sleep(6000);
        shoot(w.checkDialog(), out.resolve("amade-bookeditor-check.png"));
        System.exit(0);
    }

    private static void togglePreview(MainWindow w) {
        for (java.awt.Component c : components(w.getContentPane())) {
            if (c instanceof javax.swing.JToggleButton b && "toolbar.preview".equals(b.getName())) b.doClick();
        }
    }

    private static void invoke(Object target, String method) {
        try {
            var m = target.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<java.awt.Component> components(java.awt.Container c) {
        List<java.awt.Component> all = new ArrayList<>();
        for (java.awt.Component child : c.getComponents()) {
            all.add(child);
            if (child instanceof java.awt.Container sub) all.addAll(components(sub));
        }
        return all;
    }

    private static void shoot(Window w, Path file) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            JComponent root = ((javax.swing.RootPaneContainer) w).getRootPane();
            BufferedImage i = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = i.createGraphics();
            root.printAll(g);
            g.dispose();
            img[0] = i;
        });
        ImageIO.write(img[0], "png", file.toFile());
        System.out.println("wrote " + file);
    }

    private static Block.Paragraph p(ParaStyle s, String text) {
        return new Block.Paragraph(s, text);
    }

    /** A short novel in the classic look, with a made cover and a picture. */
    static Book sample() throws Exception {
        BookInfo info = new BookInfo("urn:uuid:1b2c3d4e-5f60-4718-8a9b-0c1d2e3f4a5b", "Harbour Light", "A Novel",
                List.of(new Person("Anna Berg", Role.AUTHOR)), "en", "", 0, "", "2026", "",
                List.of("lighthouse", "North Sea"), "A lighthouse keeper, a storm and a ship that should not be out.",
                License.ALL_RIGHTS_RESERVED, "");
        Book b = new Book(info, Look.CLASSIC);
        BufferedImage cover = CoverMaker.draw(new CoverDesign("Harbour Light", "A Novel", "Anna Berg",
                CoverDesign.COLOURS[0], null, CoverDesign.Placement.TOP, CoverDesign.Typeface.SERIF), 1600, 2400);
        b.setCover(b.addResource("cover.jpg", "image/jpeg", CoverMaker.jpeg(cover)));
        BufferedImage pic = new BufferedImage(800, 450, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pic.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x1F, 0x3A, 0x5F), 0, 450, new Color(0xF3, 0xB6, 0x3A)));
        g.fillRect(0, 0, 800, 450);
        g.setColor(Color.WHITE);
        g.fillRect(380, 120, 40, 240);
        g.setColor(new Color(0xFF, 0xE0, 0x80));
        g.fillOval(370, 90, 60, 50);
        g.setColor(new Color(0x10, 0x20, 0x30));
        g.fillRect(0, 360, 800, 90);
        g.dispose();
        ByteArrayHolder png = new ByteArrayHolder();
        ImageIO.write(pic, "png", png);
        String picture = b.addResource("lighthouse.png", "image/png", png.toByteArray());
        String[] titles = {"The Keeper", "The Night of the Storm", "Morning", "The Letter", "Ashore"};
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            List<Block> blocks = new ArrayList<>();
            blocks.add(p(ParaStyle.CHAPTER_TITLE, titles[i]));
            if (i == 1) {
                blocks.add(p(ParaStyle.EPIGRAPH, "The sea never once asked for permission."));
                blocks.add(p(ParaStyle.TEXT, "The barometer had been falling since noon. Jonas tapped the glass twice, "
                        + "as his father used to, and wrote the number into the logbook with a hand that was not quite steady."));
                blocks.add(new Block.Paragraph(ParaStyle.TEXT, List.of(
                        new Inline.Text("By six the gulls had gone inland. By seven the first squall came over the "),
                        new Inline.Text("Nordmole", Set.of(Mark.ITALIC), ""),
                        new Inline.Text(", and the lamp turned as it had turned for forty years"),
                        new Inline.Footnote("The lamp of the Nordmole light turns once every twelve seconds."),
                        new Inline.Text("."))));
                blocks.add(new Block.Picture(picture, "A white lighthouse against an orange evening sky",
                        "The light at Nordmole", PictureSize.MEDIUM));
                blocks.add(p(ParaStyle.TEXT, "He climbed the hundred and twelve steps without counting them. At the top "
                        + "the wind pressed against the glass like a hand, and somewhere out in the dark a ship was "
                        + "sounding its horn."));
                blocks.add(new Block.SceneBreak());
                blocks.add(p(ParaStyle.TEXT, "When the radio crackled, it was Marta from the harbour office. "
                        + "“There is a boat out,” she said. “The Wilhelmina. She should not be out.”"));
            } else {
                for (int k = 0; k < 6; k++) {
                    blocks.add(p(ParaStyle.TEXT, "The harbour lay quiet under a grey sky, and the keeper walked along "
                            + "the wall as he did every morning, counting the boats that had come home."));
                }
            }
            chapters.add(new Chapter("ch" + i, true, blocks));
        }
        b.setChapters(chapters);
        return b;
    }

    private static final class ByteArrayHolder extends java.io.ByteArrayOutputStream {
    }
}
