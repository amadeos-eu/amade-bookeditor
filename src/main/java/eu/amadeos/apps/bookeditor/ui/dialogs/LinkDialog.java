package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.List;
import java.util.Optional;

/**
 * Link: to a chapter of the book (chosen by title, so it survives renaming and moving) or to a web page. With
 * nothing selected, the text to link is typed here.
 */
public final class LinkDialog extends JDialog {
    /** A chapter to choose: its id and what the list shows. */
    public record Target(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** The answer: the link target (empty removes the link) and, for a new link, its text. */
    public record Answer(String target, String text) {
    }

    private final JTextField text = new JTextField(30);
    private final JRadioButton toChapter;
    private final JRadioButton toWeb;
    private final JComboBox<Target> chapters;
    private final JTextField web = new JTextField(30);
    private Answer result;

    public LinkDialog(Window owner, Messages msg, List<Target> targets, String current, String selectedText) {
        super(owner, msg.get("link.title"), ModalityType.APPLICATION_MODAL);
        setName("link");
        chapters = new JComboBox<>(targets.toArray(new Target[0]));
        toChapter = new JRadioButton(msg.get("link.chapter"));
        toWeb = new JRadioButton(msg.get("link.web"));
        ButtonGroup group = new ButtonGroup();
        group.add(toChapter);
        group.add(toWeb);
        web.setName("link.web");
        text.setName("link.text");
        Optional<String> chapterId = Link.chapterId(current);
        if (chapterId.isPresent()) {
            toChapter.setSelected(true);
            targets.stream().filter(t -> t.id.equals(chapterId.get())).findFirst().ifPresent(chapters::setSelectedItem);
        } else if (!current.isEmpty()) {
            toWeb.setSelected(true);
            web.setText(current);
        } else {
            toChapter.setSelected(!targets.isEmpty());
            toWeb.setSelected(targets.isEmpty());
            web.setText("https://");
        }
        chapters.addActionListener(e -> toChapter.setSelected(true));
        web.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                toWeb.setSelected(true);
            }
        });
        Forms.Grid form = new Forms.Grid();
        if (selectedText.isEmpty()) form.row(msg.get("link.text"), text);
        form.full(toChapter).row("", chapters).full(toWeb).row("", web).filler();
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(form, BorderLayout.CENTER);
        JButton cancel = new JButton(msg.get("button.cancel"));
        JButton remove = new JButton(msg.get("link.remove"));
        JButton ok = new JButton(msg.get("button.ok"));
        ok.setName("link.ok");
        remove.setEnabled(!current.isEmpty());
        cancel.addActionListener(e -> dispose());
        remove.addActionListener(e -> {
            result = new Answer("", "");
            dispose();
        });
        ok.addActionListener(e -> {
            String target = toChapter.isSelected() && chapters.getSelectedItem() instanceof Target t ? Link.chapter(t.id)
                    : normalise(web.getText());
            String label = selectedText.isEmpty() ? text.getText().strip() : selectedText;
            if (label.isEmpty() && toChapter.isSelected() && chapters.getSelectedItem() instanceof Target t) label = t.label;
            if (label.isEmpty()) label = target;
            result = new Answer(target, label);
            dispose();
        });
        root.add(Forms.buttons(this, cancel, remove, ok), BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    /** "amadeos.eu" becomes "https://amadeos.eu", an e-mail address a mailto link. */
    static String normalise(String address) {
        String a = address.strip();
        if (a.isEmpty() || a.equals("https://")) return "";
        if (a.matches("[^@\\s/]+@[^@\\s/]+\\.[a-zA-Z]{2,}")) return "mailto:" + a;
        if (!a.matches("(?i)(https?|mailto):.*")) return "https://" + a;
        return a;
    }

    public JTextField webField() {
        return web;
    }

    public JRadioButton webChoice() {
        return toWeb;
    }

    public JButton okButton() {
        return (JButton) getRootPane().getDefaultButton();
    }

    public Optional<Answer> ask() {
        setVisible(true);
        return Optional.ofNullable(result);
    }
}
