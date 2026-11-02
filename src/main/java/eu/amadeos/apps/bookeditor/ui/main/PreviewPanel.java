package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.epub.ChapterWriter;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.typeset.Appearance;
import eu.amadeos.commons.typeset.ChapterLayout;
import eu.amadeos.commons.typeset.ChapterParser;
import eu.amadeos.commons.typeset.ChapterText;
import eu.amadeos.commons.typeset.Css;
import eu.amadeos.commons.typeset.Hyphenators;
import eu.amadeos.commons.typeset.Page;
import eu.amadeos.commons.typeset.Typesetter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The chapter set into pages by the same typesetter as amadeOS Books, on a phone, a tablet or an e-reader, in
 * day, sepia or night colours. Setting runs in the background a moment after the last change.
 */
public final class PreviewPanel extends JPanel {

    /** The screens to preview on: the size of the text area in pixels and the body text size. */
    public enum Device {
        PHONE("phone", 300, 520, 15), TABLET("tablet", 460, 620, 17), EREADER("ereader", 380, 540, 16);

        final String key;
        final int width;
        final int height;
        final int textSize;

        Device(String key, int width, int height, int textSize) {
            this.key = key;
            this.width = width;
            this.height = height;
            this.textSize = textSize;
        }

        public String key() {
            return key;
        }

        public static Device of(String key) {
            for (Device d : values()) if (d.key.equals(key)) return d;
            return PHONE;
        }
    }

    /** Page and text colours. */
    public enum Colours {
        DAY("day", new Color(0xFB, 0xF8, 0xF1), new Color(0x22, 0x22, 0x22)),
        SEPIA("sepia", new Color(0xF4, 0xEC, 0xD8), new Color(0x5B, 0x46, 0x36)),
        NIGHT("night", new Color(0x1E, 0x1E, 0x1E), new Color(0xDD, 0xDD, 0xDD));

        final String key;
        final Color paper;
        final Color ink;

        Colours(String key, Color paper, Color ink) {
            this.key = key;
            this.paper = paper;
            this.ink = ink;
        }

        public String key() {
            return key;
        }

        public static Colours of(String key) {
            for (Colours c : values()) if (c.key.equals(key)) return c;
            return DAY;
        }
    }

    private static final int MARGIN = 18;

    private final Messages msg;
    private final Hyphenators hyphenators;
    private final ExecutorService background;
    private final AtomicLong generation = new AtomicLong();
    private final JComboBox<Device> deviceBox = new JComboBox<>(Device.values());
    private final JComboBox<Colours> coloursBox = new JComboBox<>(Colours.values());
    private final JLabel pageLabel = new JLabel(" ", SwingConstants.CENTER);
    private final Screen screen = new Screen();
    private final javax.swing.Timer delay;
    private Function<String, BufferedImage> images = n -> null;
    private Book book;
    private Chapter chapter;
    private ChapterLayout layout;
    private int page;
    private Runnable onChange = () -> {
    };

    public PreviewPanel(Messages msg, Hyphenators hyphenators, ExecutorService background) {
        super(new BorderLayout(0, 6));
        this.msg = msg;
        this.hyphenators = hyphenators;
        this.background = background;
        setName("preview");
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        top.add(new JLabel(msg.get("preview.as")));
        deviceBox.setRenderer(Renderers.keyed(msg, "preview.device.", Device::key));
        coloursBox.setRenderer(Renderers.keyed(msg, "preview.colours.", Colours::key));
        deviceBox.setName("preview.device");
        coloursBox.setName("preview.colours");
        top.add(deviceBox);
        top.add(coloursBox);
        add(top, BorderLayout.NORTH);
        add(screen, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        JButton back = new JButton("←");
        JButton next = new JButton("→");
        back.setName("preview.back");
        next.setName("preview.next");
        back.setToolTipText(msg.get("preview.back"));
        next.setToolTipText(msg.get("preview.next"));
        back.addActionListener(e -> turn(-1));
        next.addActionListener(e -> turn(1));
        bottom.add(back, BorderLayout.WEST);
        bottom.add(pageLabel, BorderLayout.CENTER);
        bottom.add(next, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        delay = new javax.swing.Timer(600, e -> typeset());
        delay.setRepeats(false);
        deviceBox.addActionListener(e -> {
            onChange.run();
            typeset();
        });
        coloursBox.addActionListener(e -> {
            onChange.run();
            screen.repaint();
        });
        setPreferredSize(new Dimension(340, 600));
    }

    /** Called when the device or colours change, to remember them. */
    public void setOnChange(Runnable r) {
        onChange = r;
    }

    public Device device() {
        return (Device) deviceBox.getSelectedItem();
    }

    public Colours colours() {
        return (Colours) coloursBox.getSelectedItem();
    }

    public void setDevice(Device d) {
        deviceBox.setSelectedItem(d);
    }

    public void setColours(Colours c) {
        coloursBox.setSelectedItem(c);
    }

    public void setImages(Function<String, BufferedImage> images) {
        this.images = images;
    }

    /** Shows {@code chapter} of {@code book} after a short pause (the writer may still be typing). */
    public void show(Book book, Chapter chapter, boolean now) {
        this.book = book;
        this.chapter = chapter;
        if (now) typeset();
        else delay.restart();
    }

    /** The page shown and how many there are, for tests. */
    int[] pageAndCount() {
        return new int[]{page, layout == null ? 0 : layout.pageCount()};
    }

    // --8<-- [start:typeset]
    private void typeset() {
        delay.stop();
        if (book == null || chapter == null || !isShowing() && !Boolean.getBoolean("bookeditor.preview.hidden")) return;
        long ticket = generation.incrementAndGet();
        String xhtml = ChapterWriter.write(book, chapter);
        Look look = book.look();
        String language = book.info().language();
        Device device = device();
        boolean sans = look == Look.MODERN || look == Look.CHILDREN;
        Appearance appearance = new Appearance(sans ? "DejaVu Sans" : "DejaVu Serif",
                look == Look.CHILDREN ? device.textSize + 3 : device.textSize, 1.4, 0, look != Look.MODERN
                && look != Look.CHILDREN && look != Look.POETRY, true);
        Function<String, BufferedImage> pictures = images;
        background.execute(() -> {
            String path = "EPUB/text/" + ChapterWriter.fileName(chapter);
            ChapterText text = ChapterParser.parse(xhtml, path, new Css().add(look.css()), language);
            Typesetter typesetter = new Typesetter(appearance, hyphenators, p -> Optional.ofNullable(pictures.apply(name(p)))
                    .map(img -> new Dimension(img.getWidth(), img.getHeight())), Map.of());
            ChapterLayout result = typesetter.layout(text, device.width - 2 * MARGIN, device.height - 2 * MARGIN);
            SwingUtilities.invokeLater(() -> {
                if (ticket != generation.get()) return;
                layout = result;
                page = Math.clamp(page, 0, Math.max(0, result.pageCount() - 1));
                showPage();
            });
        });
    }
    // --8<-- [end:typeset]

    private static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void turn(int by) {
        if (layout == null) return;
        page = Math.clamp(page + by, 0, Math.max(0, layout.pageCount() - 1));
        showPage();
    }

    private void showPage() {
        int count = layout == null ? 0 : layout.pageCount();
        pageLabel.setText(count == 0 ? " " : msg.format("preview.page", page + 1, count));
        screen.repaint();
    }

    /** The device with the page on its screen, scaled to fit the panel. */
    private final class Screen extends JComponent {
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Device d = device();
            int bezel = d == Device.PHONE ? 12 : 18;
            int top = d == Device.PHONE ? 26 : 18;
            int fw = d.width + 2 * bezel, fh = d.height + top + bezel + (d == Device.PHONE ? 10 : 0);
            double scale = Math.min(1.0, Math.min((getWidth() - 8.0) / fw, (getHeight() - 8.0) / fh));
            g.translate((getWidth() - fw * scale) / 2, 4);
            g.scale(scale, scale);
            g.setColor(d == Device.EREADER ? new Color(0x4A, 0x4A, 0x4A) : new Color(0x2B, 0x2B, 0x2B));
            g.fillRoundRect(0, 0, fw, fh, 34, 34);
            Colours c = colours();
            g.setColor(c.paper);
            g.fillRect(bezel, top, d.width, d.height);
            g.translate(bezel + MARGIN, top + MARGIN);
            if (layout != null && layout.pageCount() > 0) {
                paintPage(g, layout.pages().get(Math.min(page, layout.pageCount() - 1)), c);
            } else {
                g.setColor(Color.GRAY);
                g.setFont(getFont());
                FontMetrics fm = g.getFontMetrics();
                String wait = msg.get("preview.setting");
                g.drawString(wait, (d.width - 2 * MARGIN - fm.stringWidth(wait)) / 2, d.height / 2 - MARGIN);
            }
            g.dispose();
        }

        private void paintPage(Graphics2D g, Page p, Colours c) {
            for (Page.Picture pic : p.pictures()) {
                BufferedImage img = images.apply(name(pic.path()));
                if (img != null) {
                    g.drawImage(img, Math.round(pic.x()), Math.round(pic.y()), Math.round(pic.width()), Math.round(pic.height()), null);
                } else {
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawRect(Math.round(pic.x()), Math.round(pic.y()), Math.round(pic.width()), Math.round(pic.height()));
                }
            }
            g.setColor(c.ink);
            for (Page.Rule r : p.rules()) g.draw(new Line2D.Float(r.x1(), r.y(), r.x2(), r.y()));
            for (Page.Line line : p.lines()) line.layout().draw(g, line.x(), line.baseline());
            g.setFont(new Font("DejaVu Sans", Font.PLAIN, 10));
            g.setColor(Color.GRAY);
            String number = String.valueOf(page + 1);
            FontMetrics fm = g.getFontMetrics();
            Device d = device();
            g.drawString(number, (d.width - 2 * MARGIN - fm.stringWidth(number)) / 2f, d.height - MARGIN - 4);
        }
    }
}
