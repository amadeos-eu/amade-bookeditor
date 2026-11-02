package eu.amadeos.apps.bookeditor.ui.dialogs;

import eu.amadeos.apps.bookeditor.domain.check.Finding;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Check Book: what is missing or wrong, in plain words, each with a button that leads to it; below the editor's
 * own checks the result of EPUBCheck, which arrives a moment later.
 */
public final class CheckDialog extends JDialog {
    public static final Color PROBLEM = new Color(0xE6, 0x8A, 0x00);
    public static final Color HINT = new Color(0x1E, 0x88, 0xE5);
    public static final Color OK = new Color(0x2E, 0x7D, 0x32);

    /** The state of the standard check (EPUBCheck). */
    public sealed interface Standard {
        record Running() implements Standard {
        }

        record Missing() implements Standard {
        }

        record Failed(String reason) implements Standard {
        }

        record Done(String version, List<Finding> findings) implements Standard {
        }
    }

    private final Messages msg;
    private final Function<String, String> chapterTitles;
    private final Consumer<Finding> act;
    private final JLabel head = new JLabel();
    private final JPanel list = new JPanel();
    private final JCheckBox whileWriting;
    private List<Finding> findings = List.of();
    private Standard standard = new Standard.Running();
    private String summary = "";

    /**
     * @param chapterTitles the title of a chapter by id, for the messages
     * @param act           what a finding's button does
     * @param recheck       runs the checks again (Check Again)
     */
    public CheckDialog(Window owner, Messages msg, Function<String, String> chapterTitles, Consumer<Finding> act,
                       Runnable recheck, boolean checkWhileWriting, Consumer<Boolean> setWhileWriting) {
        super(owner, msg.get("check.title"), ModalityType.MODELESS);
        this.msg = msg;
        this.chapterTitles = chapterTitles;
        this.act = act;
        setName("check-book");
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(head, BorderLayout.NORTH);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel top = new JPanel(new BorderLayout());
        top.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);
        whileWriting = new JCheckBox(msg.get("check.while-writing"), checkWhileWriting);
        whileWriting.addActionListener(e -> setWhileWriting.accept(whileWriting.isSelected()));
        JButton again = new JButton(msg.get("check.again"));
        JButton close = new JButton(msg.get("button.close"));
        again.addActionListener(e -> recheck.run());
        close.addActionListener(e -> setVisible(false));
        JPanel south = new JPanel(new BorderLayout());
        south.add(whileWriting, BorderLayout.WEST);
        south.add(Forms.buttons(this, close, again), BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(720, 500));
        setLocationRelativeTo(owner);
        rebuild();
    }

    /** The editor's own findings and a line of what is fine (counts of chapters, pictures, notes). */
    public void showFindings(List<Finding> own, String fineSummary) {
        findings = List.copyOf(own);
        summary = fineSummary;
        rebuild();
    }

    public void showStandard(Standard state) {
        standard = state;
        rebuild();
    }

    /** All findings shown, the standard check's included, for tests. */
    public List<Finding> shownFindings() {
        List<Finding> all = new java.util.ArrayList<>(findings);
        if (standard instanceof Standard.Done d) all.addAll(d.findings());
        return all;
    }

    private void rebuild() {
        list.removeAll();
        List<Finding> all = shownFindings();
        long problems = all.stream().filter(f -> f.kind().severity() == Finding.Severity.PROBLEM).count();
        int count = all.size();
        head.setText("<html>" + (count == 0 ? "<b>" + msg.get("check.head.ready") + "</b>"
                : problems == 0 ? "<b>" + msg.get("check.head.hints") + "</b> " + msg.format("check.head.count", count)
                : "<b>" + msg.get("check.head.almost") + "</b> " + msg.format("check.head.count", count)) + "</html>");
        for (Finding f : findings) list.add(row(f));
        if (!summary.isEmpty()) list.add(row("✓", OK, msg.get("check.fine.title"), summary, null, null));
        switch (standard) {
            case Standard.Running r -> list.add(row("…", Color.GRAY, msg.get("check.standard.running.title"),
                    msg.get("check.standard.running.text"), null, null));
            case Standard.Missing m -> list.add(row("i", HINT, msg.get("check.standard.missing.title"),
                    msg.get("check.standard.missing.text"), null, null));
            case Standard.Failed failed -> list.add(row("i", HINT, msg.get("check.standard.failed.title"),
                    failed.reason(), null, null));
            case Standard.Done d -> {
                for (Finding f : d.findings()) list.add(row(f));
                if (d.findings().isEmpty()) {
                    list.add(row("✓", OK, msg.get("check.standard.passed.title"),
                            msg.format("check.standard.passed.text", d.version()), null, null));
                }
            }
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel row(Finding f) {
        boolean problem = f.kind().severity() == Finding.Severity.PROBLEM;
        String chapter = f.chapterId().isEmpty() ? "" : chapterTitles.apply(f.chapterId());
        String key = "check." + f.kind().key();
        String button = f.kind().action() == Finding.Action.NONE ? null : msg.get("check.action." + f.kind().action().name()
                .toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        Runnable run = f.kind().action() == Finding.Action.DETAILS_TEXT ? () -> showText(f.detail()) : () -> act.accept(f);
        String text = msg.format(key + ".text", escape(f.detail()), escape(chapter));
        return row(problem ? "!" : "i", problem ? PROBLEM : HINT, msg.get(key + ".title"), text, button, run);
    }

    private void showText(String text) {
        JTextArea area = new JTextArea(text, 6, 50);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), msg.get("check.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static String escape(String s) {
        String t = s.length() > 160 ? s.substring(0, 157) + "…" : s;
        return t.replace("&", "&amp;").replace("<", "&lt;");
    }

    private JPanel row(String sign, Color colour, String title, String text, String button, Runnable action) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 30)),
                BorderFactory.createEmptyBorder(8, 4, 8, 4)));
        JLabel badge = new JLabel(sign, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(colour);
                g.fillOval(2, 2, 26, 26);
                g.setColor(Color.WHITE);
                g.setFont(new Font("DejaVu Sans", Font.BOLD, 15));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(sign, 15 - fm.stringWidth(sign) / 2, 21);
                g.dispose();
            }
        };
        badge.setPreferredSize(new Dimension(30, 30));
        JPanel left = new JPanel(new BorderLayout());
        left.add(badge, BorderLayout.NORTH);
        r.add(left, BorderLayout.WEST);
        // a fixed width in the HTML, so that the label knows its height with the text wrapped
        JLabel label = new JLabel("<html><div style='width:380px'><b>" + title + "</b><br>" + text + "</div></html>");
        r.add(label, BorderLayout.CENTER);
        if (button != null) {
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
            JButton b = new JButton(button);
            b.addActionListener(e -> action.run());
            bp.add(b);
            r.add(bp, BorderLayout.EAST);
        }
        r.setAlignmentX(0);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
        return r;
    }
}
