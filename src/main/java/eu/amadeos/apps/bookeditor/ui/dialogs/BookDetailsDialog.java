package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.License;
import eu.amadeos.apps.bookeditor.domain.book.Person;
import eu.amadeos.apps.bookeditor.domain.book.Role;
import eu.amadeos.apps.bookeditor.domain.check.BookCheck;
import eu.amadeos.apps.bookeditor.ui.main.Renderers;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Book Details: what the book says about itself. General (title, people, language, series, publisher, date,
 * ISBN, keywords), Description, Rights and Accessibility; on the left the cover with its buttons.
 */
public final class BookDetailsDialog extends JDialog {
    public static final int GENERAL = 0;
    public static final int DESCRIPTION = 1;

    /** What the cover buttons do; the dialog stays open and shows the new cover. */
    public interface CoverActions {
        Optional<BufferedImage> choosePicture();

        Optional<BufferedImage> makeCover(BookInfo current);
    }

    private final Messages msg;
    private final BookInfo original;
    private final JTextField title = new JTextField(26);
    private final JTextField subtitle = new JTextField(26);
    private final JPanel peoplePanel = new JPanel();
    private final List<PersonRow> people = new ArrayList<>();
    private final JComboBox<Object> language;
    private final JTextField series = new JTextField(16);
    private final JSpinner volume = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));
    private final JTextField publisher = new JTextField(26);
    private final JTextField published = new JTextField(10);
    private final JTextField isbn = new JTextField(16);
    private final JLabel isbnState = new JLabel(" ");
    private final JTextField keywords = new JTextField(26);
    private final JTextArea description = new JTextArea(10, 40);
    private final Map<License, JRadioButton> licenses = new EnumMap<>(License.class);
    private final JTextArea summary = new JTextArea(5, 40);
    private final JLabel coverLabel = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();
    private BookInfo result;

    public BookDetailsDialog(Window owner, Messages msg, BookInfo info, BufferedImage cover, CoverActions coverActions) {
        super(owner, msg.get("details.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.original = info;
        setName("book-details");
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // The cover
        JPanel coverSide = new JPanel();
        coverSide.setLayout(new BoxLayout(coverSide, BoxLayout.Y_AXIS));
        coverLabel.setPreferredSize(new Dimension(170, 255));
        coverLabel.setMaximumSize(new Dimension(170, 255));
        coverLabel.setHorizontalAlignment(JLabel.CENTER);
        coverLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        coverLabel.setAlignmentX(0);
        showCover(cover);
        coverSide.add(coverLabel);
        coverSide.add(javax.swing.Box.createVerticalStrut(8));
        JButton choose = new JButton(msg.get("details.cover.choose"));
        JButton make = new JButton(msg.get("details.cover.make"));
        for (JButton b : new JButton[]{choose, make}) {
            b.setAlignmentX(0);
            b.setMaximumSize(new Dimension(170, b.getPreferredSize().height));
            coverSide.add(b);
            coverSide.add(javax.swing.Box.createVerticalStrut(4));
        }
        choose.addActionListener(e -> coverActions.choosePicture().ifPresent(this::showCover));
        make.addActionListener(e -> coverActions.makeCover(collect()).ifPresent(this::showCover));
        JLabel coverHint = Forms.hint(msg.get("details.cover.hint"));
        coverHint.setAlignmentX(0);
        coverHint.setMaximumSize(new Dimension(170, 80));
        coverSide.add(coverHint);
        root.add(coverSide, BorderLayout.WEST);

        // General
        title.setName("details.book-title");
        title.setText(info.title());
        subtitle.setText(info.subtitle());
        peoplePanel.setLayout(new BoxLayout(peoplePanel, BoxLayout.Y_AXIS));
        for (Person p : info.people()) addPerson(p);
        if (people.isEmpty()) addPerson(new Person("", Role.AUTHOR));
        JButton addPerson = new JButton(msg.get("details.add-person"));
        addPerson.addActionListener(e -> {
            addPerson(new Person("", Role.ILLUSTRATOR));
            pack();
        });
        language = BookLanguages.box(info.language());
        series.setText(info.series());
        volume.setValue(info.volume());
        publisher.setText(info.publisher());
        published.setText(info.published());
        isbn.setText(info.isbn());
        isbn.setName("details.isbn");
        isbn.getDocument().addDocumentListener(new Change(this::checkIsbn));
        checkIsbn();
        keywords.setText(String.join(", ", info.keywords()));
        Forms.Grid general = new Forms.Grid();
        general.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        general.row(msg.get("details.book-title"), title)
                .row(msg.get("details.subtitle"), subtitle)
                .row(msg.get("details.people"), peoplePanel)
                .row("", Forms.row(addPerson))
                .row(msg.get("details.language"), language)
                .row(msg.get("details.series"), Forms.row(series, new JLabel(msg.get("details.volume")), volume))
                .row(msg.get("details.publisher"), publisher)
                .row(msg.get("details.published"), Forms.row(published, Forms.hint(msg.get("details.published.hint"))))
                .row(msg.get("details.isbn"), Forms.row(isbn, isbnState))
                .row(msg.get("details.keywords"), keywords)
                .full(Forms.hint(msg.get("details.isbn.hint")))
                .filler();
        tabs.addTab(msg.get("details.tab.general"), general);

        // Description
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setText(info.description());
        description.setName("details.description");
        JPanel desc = new JPanel(new BorderLayout(0, 6));
        desc.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        desc.add(Forms.hint(msg.get("details.description.hint")), BorderLayout.NORTH);
        desc.add(new JScrollPane(description), BorderLayout.CENTER);
        tabs.addTab(msg.get("details.tab.description"), desc);

        // Rights
        JPanel rights = new JPanel();
        rights.setLayout(new BoxLayout(rights, BoxLayout.Y_AXIS));
        rights.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rights.add(Forms.hint(msg.get("details.rights.hint")));
        ButtonGroup group = new ButtonGroup();
        for (License l : License.values()) {
            JRadioButton b = new JRadioButton(msg.get("license." + l.key()));
            b.setName("license." + l.key());
            b.setSelected(l == info.license());
            group.add(b);
            licenses.put(l, b);
            rights.add(b);
        }
        tabs.addTab(msg.get("details.tab.rights"), rights);

        // Accessibility
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setText(info.summary());
        JPanel access = new JPanel(new BorderLayout(0, 6));
        access.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        access.add(Forms.hint(msg.get("details.accessibility.hint")), BorderLayout.NORTH);
        access.add(new JScrollPane(summary), BorderLayout.CENTER);
        access.add(Forms.hint(msg.get("details.accessibility.auto")), BorderLayout.SOUTH);
        tabs.addTab(msg.get("details.tab.accessibility"), access);
        root.add(tabs, BorderLayout.CENTER);

        JButton cancel = new JButton(msg.get("button.cancel"));
        JButton ok = new JButton(msg.get("button.ok"));
        ok.setName("details.ok");
        cancel.addActionListener(e -> dispose());
        ok.addActionListener(e -> {
            result = collect();
            dispose();
        });
        root.add(Forms.buttons(this, cancel, ok), BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private void showCover(BufferedImage cover) {
        if (cover == null) {
            coverLabel.setIcon(null);
            coverLabel.setText(msg.get("details.cover.none"));
            return;
        }
        double scale = Math.min(170.0 / cover.getWidth(), 255.0 / cover.getHeight());
        Image small = cover.getScaledInstance((int) (cover.getWidth() * scale), (int) (cover.getHeight() * scale), Image.SCALE_SMOOTH);
        coverLabel.setText(null);
        coverLabel.setIcon(new javax.swing.ImageIcon(small));
    }

    private void checkIsbn() {
        String v = isbn.getText().strip();
        isbnState.setText(v.isEmpty() ? msg.get("details.isbn.optional")
                : BookCheck.validIsbn(v) ? "✓" : msg.get("details.isbn.wrong"));
        isbnState.setForeground(v.isEmpty() || BookCheck.validIsbn(v) ? new Color(0x2E, 0x7D, 0x32) : new Color(0xC6, 0x28, 0x28));
        if (v.isEmpty()) isbnState.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
    }

    private void addPerson(Person p) {
        PersonRow row = new PersonRow(p);
        people.add(row);
        peoplePanel.add(row);
        peoplePanel.revalidate();
    }

    BookInfo collect() {
        List<Person> list = people.stream().map(PersonRow::person).toList();
        License license = licenses.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                .findFirst().orElse(License.ALL_RIGHTS_RESERVED);
        List<String> words = List.of(keywords.getText().split("[,;]"));
        return new BookInfo(original.identifier(), title.getText(), subtitle.getText(), list, BookLanguages.code(language),
                series.getText(), (Integer) volume.getValue(), publisher.getText(), published.getText(), isbn.getText(),
                words, description.getText(), license, summary.getText());
    }

    public void showTab(int index) {
        tabs.setSelectedIndex(index);
    }

    public JTextField titleField() {
        return title;
    }

    /** Shows the dialog; the new details, or empty when cancelled. */
    public Optional<BookInfo> ask() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    /** A person: name, role and a button to take them off. */
    private final class PersonRow extends JPanel {
        private final JTextField name = new JTextField(16);
        private final JComboBox<Role> role = new JComboBox<>(Role.values());

        PersonRow(Person p) {
            super(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 1));
            name.setText(p.name());
            name.setName("details.person");
            role.setSelectedItem(p.role());
            role.setRenderer(Renderers.keyed(msg, "role.", Role::key));
            JButton remove = new JButton("−");
            remove.setToolTipText(msg.get("details.remove-person"));
            remove.addActionListener(e -> {
                people.remove(this);
                peoplePanel.remove(this);
                peoplePanel.revalidate();
                BookDetailsDialog.this.pack();
            });
            add(name);
            add(role);
            add(remove);
        }

        Person person() {
            return new Person(name.getText(), (Role) role.getSelectedItem());
        }
    }

    /** Runs an action on every change of a text field. */
    record Change(Runnable action) implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}
