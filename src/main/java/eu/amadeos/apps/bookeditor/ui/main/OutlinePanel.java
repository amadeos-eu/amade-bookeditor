package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The outline of the book on the left: cover, title page, dedication, contents, the chapters, the pictures and
 * the book details. Chapters can be added, moved and deleted with the buttons below.
 */
public final class OutlinePanel extends JPanel {
    /** One line of the outline. */
    public record Item(Kind kind, String chapterId, String label) {
        public boolean chapter() {
            return kind == Kind.CHAPTER || kind == Kind.DEDICATION;
        }
    }

    public enum Kind {
        COVER, TITLE_PAGE, DEDICATION, CONTENTS, SEPARATOR, CHAPTER, PICTURES, DETAILS
    }

    private final DefaultListModel<Item> model = new DefaultListModel<>();
    private final JList<Item> list = new JList<>(model);
    private final JButton add;
    private final JButton up;
    private final JButton down;
    private final JButton delete;
    private boolean updating;

    public OutlinePanel(Messages msg, Consumer<Item> open, Runnable addChapter, Consumer<Integer> move, Runnable deleteChapter,
                        Supplier<JPopupMenu> chapterMenu) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));
        JLabel caption = new JLabel(msg.get("outline.caption"));
        caption.setFont(caption.getFont().deriveFont(Font.BOLD));
        caption.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        add(caption, BorderLayout.NORTH);
        list.setName("outline");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            if (updating || e.getValueIsAdjusting()) return;
            Item item = list.getSelectedValue();
            if (item != null && item.kind() != Kind.SEPARATOR) open.accept(item);
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = list.locationToIndex(e.getPoint());
                if (index < 0 || model.get(index).kind() != Kind.CHAPTER) return;
                list.setSelectedIndex(index);
                chapterMenu.get().show(list, e.getX(), e.getY());
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(230, 400));
        add(scroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        add = new JButton(msg.get("outline.add"));
        up = new JButton("↑");
        down = new JButton("↓");
        delete = new JButton("−");
        add.setName("outline.add");
        up.setName("outline.up");
        down.setName("outline.down");
        delete.setName("outline.delete");
        add.setToolTipText(msg.get("outline.add.tip"));
        up.setToolTipText(msg.get("outline.up.tip"));
        down.setToolTipText(msg.get("outline.down.tip"));
        delete.setToolTipText(msg.get("outline.delete.tip"));
        add.addActionListener(e -> addChapter.run());
        up.addActionListener(e -> move.accept(-1));
        down.addActionListener(e -> move.accept(1));
        delete.addActionListener(e -> deleteChapter.run());
        buttons.add(add);
        buttons.add(up);
        buttons.add(down);
        buttons.add(delete);
        add(buttons, BorderLayout.SOUTH);
        updateButtons();
        list.addListSelectionListener(e -> updateButtons());
    }

    /** Replaces the lines and selects the one for {@code selectedKind}/{@code selectedId}, without opening it. */
    public void setItems(List<Item> items, Kind selectedKind, String selectedId) {
        updating = true;
        try {
            model.clear();
            int select = -1;
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                model.addElement(it);
                if (it.kind() == selectedKind && (selectedId == null || selectedId.equals(it.chapterId()))) select = i;
            }
            if (select >= 0) list.setSelectedIndex(select);
            else list.clearSelection();
        } finally {
            updating = false;
        }
        updateButtons();
    }

    /** Selects a line and opens it. */
    public void select(Kind kind, String chapterId) {
        for (int i = 0; i < model.size(); i++) {
            Item it = model.get(i);
            if (it.kind() == kind && (chapterId == null || chapterId.equals(it.chapterId()))) {
                if (list.getSelectedIndex() == i) {
                    // already selected: open it again (after a dialog chose nothing else)
                    list.clearSelection();
                }
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    /** Selects a line without opening it. */
    public void mark(Kind kind, String chapterId) {
        updating = true;
        try {
            for (int i = 0; i < model.size(); i++) {
                Item it = model.get(i);
                if (it.kind() == kind && (chapterId == null || chapterId.equals(it.chapterId()))) list.setSelectedIndex(i);
            }
        } finally {
            updating = false;
        }
        updateButtons();
    }

    public JList<Item> outlineList() {
        return list;
    }

    private void updateButtons() {
        Item item = list.getSelectedValue();
        boolean chapter = item != null && item.kind() == Kind.CHAPTER;
        up.setEnabled(chapter);
        down.setEnabled(chapter);
        delete.setEnabled(item != null && (chapter || item.kind() == Kind.DEDICATION));
        add.setEnabled(!model.isEmpty());
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
            Item item = (Item) value;
            if (item.kind() == Kind.SEPARATOR) {
                JSeparator s = new JSeparator();
                s.setPreferredSize(new Dimension(10, 9));
                return s;
            }
            JLabel c = (JLabel) super.getListCellRendererComponent(l, item.label(), index, selected, false);
            c.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            if (!item.chapter() && !selected) c.setForeground(UIManager.getColor("Label.disabledForeground"));
            return c;
        }
    }
}
