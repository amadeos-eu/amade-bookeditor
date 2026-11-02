package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.apps.bookeditor.domain.cover.CoverDesign;
import eu.amadeos.apps.bookeditor.domain.cover.CoverMaker;
import eu.amadeos.apps.bookeditor.ui.main.Renderers;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Make a Cover: title and author on a colour or a picture, with a live preview. */
public final class CoverDialog extends JDialog {
    private final JTextField title = new JTextField(22);
    private final JTextField subtitle = new JTextField(22);
    private final JTextField author = new JTextField(22);
    private final Map<CoverDesign.Placement, JRadioButton> placements = new EnumMap<>(CoverDesign.Placement.class);
    private final JComboBox<CoverDesign.Typeface> typeface = new JComboBox<>(CoverDesign.Typeface.values());
    private final JComponent preview;
    private Color background = CoverDesign.COLOURS[0];
    private BufferedImage picture;
    private BufferedImage result;

    /**
     * @param pictures asks for a picture file (the file dialog); empty when none was chosen
     */
    public CoverDialog(Window owner, Messages msg, String titleText, String subtitleText, String authorText,
                       Supplier<Optional<BufferedImage>> pictures) {
        super(owner, msg.get("cover.title"), ModalityType.APPLICATION_MODAL);
        setName("make-cover");
        title.setText(titleText);
        subtitle.setText(subtitleText);
        author.setText(authorText);
        preview = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                BufferedImage img = CoverMaker.draw(design(), 400, 600);
                g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                g.setColor(Color.GRAY);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        };
        preview.setPreferredSize(new Dimension(240, 360));

        JPanel colours = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        ButtonGroup colourGroup = new ButtonGroup();
        for (Color c : CoverDesign.COLOURS) {
            JToggleButton b = new JToggleButton(new Swatch(c));
            b.setPreferredSize(new Dimension(30, 26));
            b.setSelected(c.equals(background));
            b.addActionListener(e -> {
                background = c;
                picture = null;
                preview.repaint();
            });
            colourGroup.add(b);
            colours.add(b);
        }
        JButton pictureButton = new JButton(msg.get("cover.picture"));
        pictureButton.addActionListener(e -> pictures.get().ifPresent(p -> {
            picture = p;
            preview.repaint();
        }));
        JPanel placement = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ButtonGroup placementGroup = new ButtonGroup();
        for (CoverDesign.Placement p : CoverDesign.Placement.values()) {
            JRadioButton b = new JRadioButton(msg.get("cover.placement." + p.key()), p == CoverDesign.Placement.TOP);
            b.addActionListener(e -> preview.repaint());
            placementGroup.add(b);
            placements.put(p, b);
            placement.add(b);
        }
        typeface.setRenderer(Renderers.keyed(msg, "cover.typeface.", CoverDesign.Typeface::key));
        typeface.addActionListener(e -> preview.repaint());
        BookDetailsDialog.Change repaint = new BookDetailsDialog.Change(preview::repaint);
        title.getDocument().addDocumentListener(repaint);
        subtitle.getDocument().addDocumentListener(repaint);
        author.getDocument().addDocumentListener(repaint);

        Forms.Grid form = new Forms.Grid();
        form.row(msg.get("cover.book-title"), title).row(msg.get("cover.subtitle"), subtitle)
                .row(msg.get("cover.author"), author).row(msg.get("cover.colour"), colours)
                .row("", Forms.row(pictureButton)).row(msg.get("cover.placement"), placement)
                .row(msg.get("cover.typeface"), typeface).full(Forms.hint(msg.get("cover.hint"))).filler();

        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(preview, BorderLayout.WEST);
        root.add(form, BorderLayout.CENTER);
        JButton cancel = new JButton(msg.get("button.cancel"));
        JButton use = new JButton(msg.get("cover.use"));
        use.setName("cover.use");
        cancel.addActionListener(e -> dispose());
        use.addActionListener(e -> {
            result = CoverMaker.draw(design(), CoverMaker.WIDTH, CoverMaker.HEIGHT);
            dispose();
        });
        root.add(Forms.buttons(this, cancel, use), BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    CoverDesign design() {
        CoverDesign.Placement p = placements.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(CoverDesign.Placement.TOP);
        return new CoverDesign(title.getText(), subtitle.getText(), author.getText(), background, picture, p,
                (CoverDesign.Typeface) typeface.getSelectedItem());
    }

    public void setPicture(BufferedImage p) {
        picture = p;
        preview.repaint();
    }

    public JButton useButton() {
        return (JButton) getRootPane().getDefaultButton();
    }

    /** The finished cover at full size, or empty when cancelled. */
    public Optional<BufferedImage> ask() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    /** A colour square for the colour buttons. */
    private record Swatch(Color colour) implements javax.swing.Icon {
        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            g.setColor(colour);
            g.fillRect(x, y, 16, 16);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, 15, 15);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
}
