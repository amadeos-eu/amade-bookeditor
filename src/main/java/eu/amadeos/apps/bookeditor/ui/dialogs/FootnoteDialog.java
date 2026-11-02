package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.Optional;

/** The text of a footnote. Readers show it in a small window when the number is tapped. */
public final class FootnoteDialog extends JDialog {
    private final JTextArea text = new JTextArea(5, 36);
    private String result;

    public FootnoteDialog(Window owner, Messages msg, int number, String current) {
        super(owner, msg.format("footnote.title", number), ModalityType.APPLICATION_MODAL);
        setName("footnote");
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setText(current);
        text.setName("footnote.text");
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(Forms.hint(msg.get("footnote.hint")), BorderLayout.NORTH);
        root.add(new JScrollPane(text), BorderLayout.CENTER);
        JButton cancel = new JButton(msg.get("button.cancel"));
        JButton ok = new JButton(msg.get("button.ok"));
        cancel.addActionListener(e -> dispose());
        ok.addActionListener(e -> {
            result = text.getText().replaceAll("\\s+", " ").strip();
            dispose();
        });
        // Enter writes in the text; Ctrl+Enter confirms
        text.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ctrl ENTER"), "confirm");
        text.getActionMap().put("confirm", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ok.doClick();
            }
        });
        root.add(Forms.buttons(this, cancel, ok), BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    public Optional<String> ask() {
        setVisible(true);
        return Optional.ofNullable(result);
    }
}
