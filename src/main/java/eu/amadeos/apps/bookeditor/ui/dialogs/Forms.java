package eu.amadeos.apps.bookeditor.ui.dialogs;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;

/** Small helpers for the dialogs: label-field grids, hint labels and OK/Cancel rows. */
final class Forms {
    private Forms() {
    }

    /** A two-column grid; {@link #row} adds a line. */
    static final class Grid extends JPanel {
        private int row;

        Grid() {
            super(new GridBagLayout());
        }

        Grid row(String label, Component field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(3, 3, 3, 8);
            JLabel l = new JLabel(label);
            if (field instanceof JComponent jc) l.setLabelFor(jc);
            add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(field, c);
            row++;
            return this;
        }

        Grid full(Component field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(3, 3, 3, 3);
            add(field, c);
            return this;
        }

        Grid filler() {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weighty = 1;
            add(new JPanel(), c);
            return this;
        }
    }

    static JLabel hint(String text) {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        return l;
    }

    static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component c : parts) p.add(c);
        return p;
    }

    /** Right-aligned buttons; the last one is the default button, Escape presses {@code cancel}. */
    static JPanel buttons(JDialog dialog, JButton cancel, JButton... others) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        p.add(cancel);
        for (JButton b : others) p.add(b);
        if (others.length > 0) dialog.getRootPane().setDefaultButton(others[others.length - 1]);
        dialog.getRootPane().registerKeyboardAction(e -> cancel.doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JRootPane.WHEN_IN_FOCUSED_WINDOW);
        return p;
    }
}
