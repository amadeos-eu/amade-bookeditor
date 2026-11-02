package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.ui.dialogs.BookDetailsDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.CoverDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.FootnoteDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.LinkDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.NewBookDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.PictureDialog;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The real dialogs. */
public final class SwingAsk implements Ask {
    private final Window owner;
    private final Messages msg;

    public SwingAsk(Window owner, Messages msg) {
        this.owner = owner;
        this.msg = msg;
    }

    @Override
    public Optional<NewBookDialog.Choice> newBook(String author, String language, Path folder) {
        return new NewBookDialog(owner, msg, author, language, folder).ask();
    }

    @Override
    public Optional<Path> openBook(Path folder) {
        JFileChooser chooser = chooser(folder);
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("file.filter.epub"), "epub"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<Path> saveAs(Path suggested) {
        JFileChooser chooser = chooser(suggested.getParent());
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("file.filter.epub"), "epub"));
        chooser.setSelectedFile(suggested.toFile());
        while (true) {
            if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return Optional.empty();
            File f = chooser.getSelectedFile();
            Path p = f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".epub") ? f.toPath()
                    : f.toPath().resolveSibling(f.getName() + ".epub");
            if (!Files.exists(p) || confirm(msg.format("file.replace", p.getFileName()))) return Optional.of(p);
        }
    }

    @Override
    public Optional<Path> pictureFile(Path folder) {
        JFileChooser chooser = chooser(folder);
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("file.filter.pictures"), "jpg", "jpeg", "png", "gif",
                "webp", "bmp"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<BookInfo> details(BookInfo info, BufferedImage cover, BookDetailsDialog.CoverActions actions, int tab) {
        BookDetailsDialog d = new BookDetailsDialog(owner, msg, info, cover, actions);
        d.showTab(tab);
        return d.ask();
    }

    @Override
    public Optional<BufferedImage> makeCover(BookInfo info, Supplier<Optional<BufferedImage>> pictures) {
        return new CoverDialog(owner, msg, info.title(), info.subtitle(), info.authorLine(), pictures).ask();
    }

    @Override
    public Optional<Block.Picture> picture(Block.Picture picture, BufferedImage image) {
        return new PictureDialog(owner, msg, picture, image).ask();
    }

    @Override
    public Optional<LinkDialog.Answer> link(List<LinkDialog.Target> targets, String current, String selectedText) {
        return new LinkDialog(owner, msg, targets, current, selectedText).ask();
    }

    @Override
    public Optional<String> footnote(int number, String current) {
        return new FootnoteDialog(owner, msg, number, current).ask();
    }

    @Override
    public Unsaved unsaved(String bookTitle) {
        Object[] options = {msg.get("unsaved.save"), msg.get("unsaved.discard"), msg.get("button.cancel")};
        int answer = JOptionPane.showOptionDialog(owner, msg.format("unsaved.text", bookTitle), msg.get("unsaved.title"),
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return switch (answer) {
            case 0 -> Unsaved.SAVE;
            case 1 -> Unsaved.DISCARD;
            default -> Unsaved.CANCEL;
        };
    }

    @Override
    public boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(owner, question, msg.get("window.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public Foreign foreign(String message) {
        Object[] options = {msg.get("open.edit-copy"), msg.get("open.in-books"), msg.get("button.cancel")};
        int answer = JOptionPane.showOptionDialog(owner, "<html><div style='width:420px'>" + message + "</div></html>",
                msg.get("open.not-editable.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                options, options[0]);
        return switch (answer) {
            case 0 -> Foreign.EDIT_COPY;
            case 1 -> Foreign.READ;
            default -> Foreign.CANCEL;
        };
    }

    @Override
    public void error(String message) {
        JOptionPane.showMessageDialog(owner, message, msg.get("window.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void info(String message) {
        JOptionPane.showMessageDialog(owner, message, msg.get("window.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static JFileChooser chooser(Path folder) {
        return new JFileChooser(folder == null || !Files.isDirectory(folder) ? null : folder.toFile());
    }
}
