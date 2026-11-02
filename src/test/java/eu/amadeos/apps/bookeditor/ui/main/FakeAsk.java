package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.ui.dialogs.BookDetailsDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.LinkDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.NewBookDialog;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Answers the main window's questions the way a test wants, and remembers what was asked. */
final class FakeAsk implements Ask {
    NewBookDialog.Choice newBook;
    Path open;
    Path saveAs;
    Path picture;
    Function<BookInfo, BookInfo> details;
    boolean detailsMakesCover;
    BufferedImage cover;
    Function<Block.Picture, Block.Picture> pictureAnswer = p -> p;
    LinkDialog.Answer link;
    String footnote;
    Unsaved unsaved = Unsaved.DISCARD;
    boolean confirm = true;
    boolean openElsewhere;
    final List<String> asked = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    List<LinkDialog.Target> linkTargets;
    int footnoteNumber;

    @Override
    public Optional<NewBookDialog.Choice> newBook(String author, String language, Path folder) {
        asked.add("new");
        return Optional.ofNullable(newBook);
    }

    @Override
    public Optional<Path> openBook(Path folder) {
        asked.add("open");
        return Optional.ofNullable(open);
    }

    @Override
    public Optional<Path> saveAs(Path suggested) {
        asked.add("save-as " + suggested.getFileName());
        return Optional.ofNullable(saveAs);
    }

    @Override
    public Optional<Path> pictureFile(Path folder) {
        asked.add("picture-file");
        return Optional.ofNullable(picture);
    }

    @Override
    public Optional<BookInfo> details(BookInfo info, BufferedImage coverNow, BookDetailsDialog.CoverActions actions, int tab) {
        asked.add("details " + tab);
        if (details == null) return Optional.empty();
        if (detailsMakesCover) actions.makeCover(info);
        return Optional.of(details.apply(info));
    }

    @Override
    public Optional<BufferedImage> makeCover(BookInfo info, Supplier<Optional<BufferedImage>> pictures) {
        asked.add("make-cover");
        return Optional.ofNullable(cover);
    }

    @Override
    public Optional<Block.Picture> picture(Block.Picture p, BufferedImage image) {
        asked.add("picture " + p.resource() + (image == null ? " (no image)" : ""));
        return Optional.ofNullable(pictureAnswer.apply(p));
    }

    @Override
    public Optional<LinkDialog.Answer> link(List<LinkDialog.Target> targets, String current, String selectedText) {
        asked.add("link " + current + " [" + selectedText + "]");
        linkTargets = targets;
        return Optional.ofNullable(link);
    }

    @Override
    public Optional<String> footnote(int number, String current) {
        asked.add("footnote " + number);
        footnoteNumber = number;
        return Optional.ofNullable(footnote);
    }

    @Override
    public Unsaved unsaved(String bookTitle) {
        asked.add("unsaved " + bookTitle);
        return unsaved;
    }

    @Override
    public boolean confirm(String question) {
        asked.add("confirm");
        return confirm;
    }

    @Override
    public boolean openElsewhere(String message) {
        asked.add("elsewhere");
        return openElsewhere;
    }

    @Override
    public void error(String message) {
        errors.add(message);
    }

    @Override
    public void info(String message) {
        infos.add(message);
    }
}
