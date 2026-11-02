package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.ui.dialogs.BookDetailsDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.LinkDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.NewBookDialog;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Everything the main window asks the writer: the dialogs and file choosers. The window gets {@link SwingAsk};
 * tests give it answers of their own, so they need no modal dialogs.
 */
public interface Ask {
    /** What to do with a book made by another program. */
    enum Foreign {
        /** Take it over into a new book; the original file stays as it is. */
        EDIT_COPY,
        /** Read it in Books. */
        READ,
        CANCEL
    }

    /** What to do with changes that are not saved yet. */
    enum Unsaved {
        SAVE, DISCARD, CANCEL
    }

    Optional<NewBookDialog.Choice> newBook(String author, String language, Path folder);

    Optional<Path> openBook(Path folder);

    /** Where to save the book; the answer ends in ".epub". */
    Optional<Path> saveAs(Path suggested);

    Optional<Path> pictureFile(Path folder);

    Optional<BookInfo> details(BookInfo info, BufferedImage cover, BookDetailsDialog.CoverActions actions, int tab);

    Optional<BufferedImage> makeCover(BookInfo info, Supplier<Optional<BufferedImage>> pictures);

    Optional<Block.Picture> picture(Block.Picture picture, BufferedImage image);

    Optional<LinkDialog.Answer> link(List<LinkDialog.Target> targets, String current, String selectedText);

    Optional<String> footnote(int number, String current);

    Unsaved unsaved(String bookTitle);

    boolean confirm(String question);

    /** A book that is not the editor's own: edit a copy, read it in Books, or neither. */
    Foreign foreign(String message);

    void error(String message);

    void info(String message);
}
