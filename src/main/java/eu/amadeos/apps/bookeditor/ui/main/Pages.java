package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Person;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import eu.amadeos.apps.bookeditor.domain.book.Role;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The pages the outline shows besides chapters: the cover, the title page and the contents (made from the book),
 * the pictures, and the welcome page before a book is open.
 */
final class Pages {
    private static final Color PAPER = Color.WHITE;

    private Pages() {
    }

    private static JPanel page() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PAPER);
        p.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));
        return p;
    }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("DejaVu Sans", Font.BOLD, 20));
        l.setAlignmentX(0);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return l;
    }

    private static JLabel hint(String text) {
        JLabel l = new JLabel("<html><div style='width:420px'>" + text + "</div></html>");
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        l.setAlignmentX(0);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        return l;
    }

    private static JPanel buttons(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (JButton b : buttons) {
            p.add(b);
            p.add(Box.createHorizontalStrut(6));
        }
        p.setAlignmentX(0);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    static JButton button(String text, String name, Runnable action) {
        JButton b = new JButton(text);
        b.setName(name);
        b.addActionListener(e -> action.run());
        return b;
    }

    static ImageIcon scaled(BufferedImage img, int maxW, int maxH) {
        double scale = Math.min(1.0, Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight()));
        return new ImageIcon(img.getScaledInstance(Math.max(1, (int) (img.getWidth() * scale)),
                Math.max(1, (int) (img.getHeight() * scale)), Image.SCALE_SMOOTH));
    }

    // ---------------------------------------------------------------- cover

    static JComponent cover(Messages msg, BufferedImage cover, Runnable choose, Runnable make, Runnable remove) {
        JPanel p = page();
        p.add(heading(msg.get("page.cover")));
        p.add(hint(msg.get("page.cover.hint")));
        JLabel picture = new JLabel();
        picture.setAlignmentX(0);
        if (cover != null) {
            picture.setIcon(scaled(cover, 300, 450));
            picture.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        } else {
            picture.setText(msg.get("page.cover.none"));
            picture.setHorizontalAlignment(SwingConstants.CENTER);
            picture.setPreferredSize(new Dimension(200, 300));
            picture.setMaximumSize(new Dimension(200, 300));
            picture.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 4, 4));
        }
        p.add(picture);
        p.add(Box.createVerticalStrut(12));
        JButton removeButton = button(msg.get("page.cover.remove"), "cover.remove", remove);
        removeButton.setEnabled(cover != null);
        p.add(buttons(button(msg.get("page.cover.choose"), "cover.choose", choose),
                button(msg.get("page.cover.make"), "cover.make", make), removeButton));
        return scroll(p);
    }

    // ---------------------------------------------------------------- title page

    static JComponent titlePage(Messages msg, BookInfo info, Runnable details) {
        JPanel p = page();
        p.add(heading(msg.get("page.title-page")));
        p.add(hint(msg.get("page.title-page.hint")));
        JPanel sheet = new JPanel();
        sheet.setLayout(new BoxLayout(sheet, BoxLayout.Y_AXIS));
        sheet.setBackground(new Color(0xFB, 0xF8, 0xF1));
        sheet.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(50, 30, 40, 30)));
        sheet.setAlignmentX(0);
        sheet.setMaximumSize(new Dimension(380, 520));
        sheet.setPreferredSize(new Dimension(380, 480));
        centred(sheet, info.title().isEmpty() ? msg.get("page.title-page.no-title") : info.title(),
                new Font("DejaVu Serif", Font.BOLD, 24), 6);
        if (!info.subtitle().isEmpty()) centred(sheet, info.subtitle(), new Font("DejaVu Serif", Font.PLAIN, 16), 24);
        sheet.add(Box.createVerticalStrut(24));
        for (Person person : info.people()) {
            boolean author = person.role() == Role.AUTHOR;
            centred(sheet, author ? person.name() : msg.format("page.title-page.role", msg.get("role." + person.role().key()),
                    person.name()), new Font("DejaVu Serif", Font.PLAIN, author ? 16 : 13), 4);
        }
        sheet.add(Box.createVerticalGlue());
        if (!info.publisher().isEmpty()) centred(sheet, info.publisher(), new Font("DejaVu Serif", Font.PLAIN, 13), 4);
        p.add(sheet);
        p.add(Box.createVerticalStrut(12));
        p.add(buttons(button(msg.get("page.title-page.edit"), "title-page.edit", details)));
        return scroll(p);
    }

    private static void centred(JPanel panel, String text, Font font, int gap) {
        JLabel l = new JLabel("<html><div style='text-align:center;width:300px'>" + text.replace("&", "&amp;")
                .replace("<", "&lt;") + "</div></html>", SwingConstants.CENTER);
        l.setFont(font);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, gap, 0));
        panel.add(l);
    }

    // ---------------------------------------------------------------- contents

    static JComponent contents(Messages msg, Book book, Function<Chapter, String> label, Consumer<String> open) {
        JPanel p = page();
        p.add(heading(msg.get("page.contents")));
        p.add(hint(msg.get("page.contents.hint")));
        for (Chapter c : book.chapters()) {
            int n = book.numberOf(c.id());
            JButton b = new JButton((n > 0 ? n + "   " : "     ") + label.apply(c));
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setAlignmentX(0);
            b.setFont(new Font("DejaVu Serif", Font.PLAIN, 15));
            b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            b.addActionListener(e -> open.accept(c.id()));
            p.add(b);
            for (Block block : c.blocks()) {
                if (block instanceof Block.Paragraph para && para.style() == eu.amadeos.apps.bookeditor.domain.book.ParaStyle.HEADING
                        && !para.isEmpty()) {
                    JLabel sub = new JLabel(para.plainText());
                    sub.setFont(new Font("DejaVu Serif", Font.PLAIN, 13));
                    sub.setBorder(BorderFactory.createEmptyBorder(0, 50, 2, 0));
                    sub.setAlignmentX(0);
                    p.add(sub);
                }
            }
        }
        return scroll(p);
    }

    // ---------------------------------------------------------------- pictures

    /** A picture of the book with the chapter it is in. */
    record PictureUse(Chapter chapter, Block.Picture picture) {
    }

    static JComponent pictures(Messages msg, Book book, Function<String, BufferedImage> images, Function<Chapter, String> label,
                               Consumer<PictureUse> edit) {
        JPanel p = page();
        p.add(heading(msg.get("page.pictures")));
        p.add(hint(msg.get("page.pictures.hint")));
        List<Map.Entry<Chapter, Block.Picture>> all = book.pictures();
        if (all.isEmpty()) p.add(hint(msg.get("page.pictures.none")));
        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setOpaque(false);
        for (Map.Entry<Chapter, Block.Picture> e : all) {
            Block.Picture pic = e.getValue();
            JPanel card = new JPanel(new BorderLayout(0, 4));
            card.setOpaque(false);
            JButton thumb = new JButton();
            BufferedImage img = images.apply(pic.resource());
            if (img != null) thumb.setIcon(scaled(img, 180, 120));
            else thumb.setText(pic.resource());
            thumb.setPreferredSize(new Dimension(190, 130));
            thumb.addActionListener(a -> edit.accept(new PictureUse(e.getKey(), pic)));
            card.add(thumb, BorderLayout.CENTER);
            Resource r = book.resource(pic.resource()).orElse(null);
            String size = r == null ? "" : " · " + (r.size() + 1023) / 1024 + " KB";
            String state = pic.description().isEmpty() ? "<font color='#E68A00'>" + msg.get("page.pictures.no-description")
                    + "</font>" : pic.description().replace("<", "&lt;");
            card.add(new JLabel("<html><div style='width:180px'><b>" + label.apply(e.getKey()).replace("<", "&lt;") + "</b>"
                    + size + "<br>" + state + "</div></html>"), BorderLayout.SOUTH);
            grid.add(card);
        }
        grid.setAlignmentX(0);
        p.add(grid);
        return scroll(p);
    }

    // ---------------------------------------------------------------- welcome

    static JComponent welcome(Messages msg, List<Path> recent, Runnable newBook, Runnable open, Consumer<Path> openRecent) {
        JPanel p = page();
        JLabel title = new JLabel(msg.get("welcome.title"));
        title.setFont(new Font("DejaVu Serif", Font.BOLD, 28));
        title.setAlignmentX(0);
        p.add(title);
        p.add(hint(msg.get("welcome.text")));
        p.add(buttons(button(msg.get("welcome.new"), "welcome.new", newBook), button(msg.get("welcome.open"), "welcome.open", open)));
        if (!recent.isEmpty()) {
            p.add(Box.createVerticalStrut(24));
            JLabel r = heading(msg.get("welcome.recent"));
            r.setFont(r.getFont().deriveFont(15f));
            p.add(r);
            for (Path file : recent) {
                JButton b = new JButton(file.getFileName().toString());
                b.setToolTipText(file.toString());
                b.setBorderPainted(false);
                b.setContentAreaFilled(false);
                b.setForeground(new Color(0x1A, 0x5F, 0xB4));
                b.setAlignmentX(0);
                b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                b.addActionListener(e -> openRecent.accept(file));
                p.add(b);
            }
        }
        return scroll(p);
    }

    private static JScrollPane scroll(JPanel p) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(PAPER);
        holder.add(p, BorderLayout.NORTH);
        JScrollPane s = new JScrollPane(holder);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getVerticalScrollBar().setUnitIncrement(16);
        return s;
    }
}
