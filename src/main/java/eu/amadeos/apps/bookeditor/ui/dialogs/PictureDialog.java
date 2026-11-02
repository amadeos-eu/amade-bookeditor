package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** The settings of a picture: the description for blind readers, the caption and the size. */
public final class PictureDialog extends JDialog {
    private final Block.Picture picture;
    private final JTextArea description = new JTextArea(3, 32);
    private final JTextField caption = new JTextField(32);
    private final Map<PictureSize, JRadioButton> sizes = new EnumMap<>(PictureSize.class);
    private Block.Picture result;

    public PictureDialog(Window owner, Messages msg, Block.Picture picture, BufferedImage image) {
        super(owner, msg.get("picture.title"), ModalityType.APPLICATION_MODAL);
        this.picture = picture;
        setName("picture-settings");
        JLabel preview = new JLabel();
        preview.setHorizontalAlignment(JLabel.CENTER);
        preview.setPreferredSize(new Dimension(220, 180));
        if (image != null) {
            double scale = Math.min(1.0, Math.min(220.0 / image.getWidth(), 180.0 / image.getHeight()));
            preview.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1, (int) (image.getWidth() * scale)),
                    Math.max(1, (int) (image.getHeight() * scale)), Image.SCALE_SMOOTH)));
        } else {
            preview.setText(picture.resource());
        }
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setText(picture.description());
        description.setName("picture.description");
        caption.setText(picture.caption());
        caption.setName("picture.caption");
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ButtonGroup group = new ButtonGroup();
        for (PictureSize s : PictureSize.values()) {
            JRadioButton b = new JRadioButton(msg.get("picture.size." + s.key()), s == picture.size());
            group.add(b);
            sizes.put(s, b);
            sizeRow.add(b);
        }
        Forms.Grid form = new Forms.Grid();
        form.full(new JLabel(msg.get("picture.description")))
                .full(new JScrollPane(description))
                .full(Forms.hint(msg.get("picture.description.hint")))
                .row(msg.get("picture.caption"), caption)
                .row(msg.get("picture.size"), sizeRow)
                .filler();
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(preview, BorderLayout.WEST);
        root.add(form, BorderLayout.CENTER);
        JButton cancel = new JButton(msg.get("button.cancel"));
        JButton ok = new JButton(msg.get("button.ok"));
        ok.setName("picture.ok");
        cancel.addActionListener(e -> dispose());
        ok.addActionListener(e -> {
            result = current();
            dispose();
        });
        root.add(Forms.buttons(this, cancel, ok), BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    Block.Picture current() {
        PictureSize size = sizes.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                .findFirst().orElse(PictureSize.MEDIUM);
        return new Block.Picture(picture.resource(), description.getText().replaceAll("\\s+", " "), caption.getText(), size);
    }

    public JTextArea descriptionField() {
        return description;
    }

    public JButton okButton() {
        return (JButton) getRootPane().getDefaultButton();
    }

    public Optional<Block.Picture> ask() {
        setVisible(true);
        return Optional.ofNullable(result);
    }
}
