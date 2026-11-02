package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** New Book: title, author, language, one of the looks, and whether to start empty or from a text file. */
public final class NewBookDialog extends JDialog {
    /** What the writer chose; {@code source} is empty for an empty book. */
    public record Choice(String title, String author, String language, Look look, Optional<Path> source) {
    }

    private final Messages msg;
    private final JTextField title = new JTextField(28);
    private final JTextField author = new JTextField(28);
    private final JComboBox<Object> language;
    private final Map<Look, LookCard> cards = new EnumMap<>(Look.class);
    private final JRadioButton empty;
    private final JRadioButton fromFile;
    private final JLabel fileLabel = new JLabel(" ");
    private Look look = Look.CLASSIC;
    private Path source;
    private Choice result;

    public NewBookDialog(Window owner, Messages msg, String author, String languageCode, Path folder) {
        super(owner, msg.get("new.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        setName("new-book");
        title.setName("new.book-title");
        this.author.setName("new.author");
        this.author.setText(author);
        language = BookLanguages.box(languageCode);
        language.setName("new.language");

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        Forms.Grid form = new Forms.Grid();
        form.row(msg.get("new.book-title"), title).row(msg.get("new.author"), this.author).row(msg.get("new.language"), language);
        form.setAlignmentX(0);
        p.add(form);
        JLabel looksLabel = new JLabel(msg.get("new.look"));
        looksLabel.setBorder(BorderFactory.createEmptyBorder(10, 3, 2, 0));
        looksLabel.setAlignmentX(0);
        p.add(looksLabel);
        JPanel looks = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (Look l : Look.values()) {
            LookCard card = new LookCard(l, msg);
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    choose(l);
                }
            });
            cards.put(l, card);
            looks.add(card);
        }
        looks.setAlignmentX(0);
        p.add(looks);

        empty = new JRadioButton(msg.get("new.start-empty"), true);
        fromFile = new JRadioButton(msg.get("new.start-file"));
        empty.setName("new.start-empty");
        fromFile.setName("new.start-file");
        ButtonGroup group = new ButtonGroup();
        group.add(empty);
        group.add(fromFile);
        fromFile.addActionListener(e -> {
            if (source == null && !chooseFile(folder)) empty.setSelected(true);
        });
        JPanel start = new JPanel();
        start.setLayout(new BoxLayout(start, BoxLayout.Y_AXIS));
        start.setBorder(BorderFactory.createTitledBorder(msg.get("new.start")));
        start.add(empty);
        start.add(fromFile);
        fileLabel.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
        start.add(fileLabel);
        JLabel hint = Forms.hint(msg.get("new.start-hint"));
        hint.setBorder(BorderFactory.createEmptyBorder(2, 24, 2, 0));
        start.add(hint);
        start.setAlignmentX(0);
        p.add(start);

        JButton cancel = new JButton(msg.get("button.cancel"));
        JButton create = new JButton(msg.get("new.create"));
        create.setName("new.create");
        cancel.addActionListener(e -> dispose());
        create.addActionListener(e -> {
            result = new Choice(title.getText().strip(), this.author.getText().strip(), BookLanguages.code(language), look,
                    fromFile.isSelected() ? Optional.ofNullable(source) : Optional.empty());
            dispose();
        });
        JPanel buttons = Forms.buttons(this, cancel, create);
        buttons.setAlignmentX(0);
        p.add(buttons);
        choose(Look.CLASSIC);
        setContentPane(p);
        pack();
        setLocationRelativeTo(owner);
    }

    private boolean chooseFile(Path folder) {
        JFileChooser chooser = new JFileChooser(folder == null ? null : folder.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("new.file-filter"), "txt", "md", "markdown"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return false;
        setSource(chooser.getSelectedFile().toPath());
        return true;
    }

    /** Takes a text file to start from (the file dialog's answer; tests call it directly). */
    public void setSource(Path file) {
        source = file;
        fileLabel.setText(file.getFileName().toString());
        fromFile.setSelected(true);
        if (title.getText().isBlank()) {
            String n = file.getFileName().toString();
            title.setText(n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n);
        }
    }

    public void choose(Look l) {
        look = l;
        cards.forEach((k, c) -> c.setSelected(k == l));
    }

    public JTextField titleField() {
        return title;
    }

    public JButton createButton() {
        return (JButton) getRootPane().getDefaultButton();
    }

    /** Shows the dialog; empty when cancelled. */
    public Optional<Choice> ask() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    /** A small picture of a page in one look, with its name. */
    static final class LookCard extends JPanel {
        private final Look look;
        private boolean selected;

        LookCard(Look look, Messages msg) {
            super(new BorderLayout(0, 3));
            this.look = look;
            setName("look." + look.key());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JComponent pic = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    paintPage((Graphics2D) g, getWidth(), getHeight());
                }
            };
            pic.setPreferredSize(new Dimension(118, 118));
            add(pic, BorderLayout.CENTER);
            JLabel name = new JLabel("<html><b>" + msg.get("look." + look.key()) + "</b><br><font size=-1>"
                    + msg.get("look." + look.key() + ".hint") + "</font></html>");
            name.setPreferredSize(new Dimension(128, 46));
            name.setVerticalAlignment(JLabel.TOP);
            add(name, BorderLayout.SOUTH);
            getAccessibleContext().setAccessibleName(msg.get("look." + look.key()));
        }

        void setSelected(boolean on) {
            selected = on;
            repaint();
        }

        boolean selected() {
            return selected;
        }

        private void paintPage(Graphics2D g, int w, int h) {
            g = (Graphics2D) g.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            Color accent = UIManager.getColor("List.selectionBackground");
            g.setColor(selected ? (accent == null ? new Color(0x00, 0x80, 0x80) : accent) : Color.GRAY);
            g.setStroke(new BasicStroke(selected ? 3 : 1));
            g.drawRect(1, 1, w - 3, h - 3);
            g.setStroke(new BasicStroke(1));
            int x = 14, y = 26;
            Color head = new Color(0x1F, 0x3A, 0x5F);
            switch (look) {
                case CLASSIC -> {
                    g.setColor(head);
                    g.setFont(new Font("DejaVu Serif", Font.BOLD, 12));
                    g.drawString("Chapter One", x, y);
                    g.setColor(new Color(0x77, 0x77, 0x77));
                    for (int i = 0; i < 8; i++) {
                        int indent = i % 4 == 0 ? 10 : 0;
                        g.fillRect(x + indent, y + 14 + i * 9, 90 - indent - (i % 4 == 3 ? 40 : 0), 3);
                    }
                }
                case MODERN -> {
                    g.setColor(new Color(0x00, 0x80, 0x80));
                    g.setFont(new Font("DejaVu Sans", Font.BOLD, 12));
                    g.drawString("1 · Start", x, y);
                    g.setColor(new Color(0x88, 0x88, 0x88));
                    for (int i = 0; i < 7; i++) {
                        int gap = i >= 3 ? 8 : 0;
                        g.fillRect(x, y + 14 + i * 8 + gap, 90 - (i == 2 || i == 6 ? 40 : 0), 3);
                    }
                }
                case CHILDREN -> {
                    g.setColor(new Color(0xF3, 0xB6, 0x3A));
                    g.fillOval(x + 20, y - 14, 50, 44);
                    g.setColor(new Color(0x4C, 0xAF, 0x50));
                    g.fillRect(x, y + 22, 90, 16);
                    g.setColor(new Color(0x22, 0x22, 0x22));
                    g.setFont(new Font("DejaVu Sans", Font.BOLD, 13));
                    g.drawString("Once upon", x + 6, y + 60);
                    g.drawString("a time…", x + 14, y + 76);
                }
                case POETRY -> {
                    g.setColor(new Color(0x22, 0x22, 0x22));
                    g.setFont(new Font("DejaVu Serif", Font.ITALIC, 11));
                    String[] lines = {"The sea is calm", "    to-night,", "The tide is full,", "    the moon lies", "    fair"};
                    for (int i = 0; i < lines.length; i++) g.drawString(lines[i], x, y + i * 15);
                }
            }
            g.dispose();
        }
    }
}
