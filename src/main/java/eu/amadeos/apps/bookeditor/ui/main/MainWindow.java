package eu.amadeos.apps.bookeditor.ui.main;

import com.google.inject.Inject;
import eu.amadeos.apps.bookeditor.bootstrap.AmadeBookEditorApplication;
import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.Inline;
import eu.amadeos.apps.bookeditor.domain.book.Link;
import eu.amadeos.apps.bookeditor.domain.book.Look;
import eu.amadeos.apps.bookeditor.domain.book.Mark;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.PictureSize;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import eu.amadeos.apps.bookeditor.domain.check.BookCheck;
import eu.amadeos.apps.bookeditor.domain.check.EpubCheckRunner;
import eu.amadeos.apps.bookeditor.domain.check.Finding;
import eu.amadeos.apps.bookeditor.domain.cover.CoverMaker;
import eu.amadeos.apps.bookeditor.domain.cover.Pictures;
import eu.amadeos.apps.bookeditor.domain.epub.EpubReader;
import eu.amadeos.apps.bookeditor.domain.epub.EpubWriter;
import eu.amadeos.apps.bookeditor.domain.epub.ForeignEpubReader;
import eu.amadeos.apps.bookeditor.domain.importer.BookImport;
import eu.amadeos.apps.bookeditor.domain.importer.LibreOfficeConverter;
import eu.amadeos.apps.bookeditor.domain.importer.TextImport;
import eu.amadeos.apps.bookeditor.repository.Backups;
import eu.amadeos.apps.bookeditor.repository.EditorPreferences;
import eu.amadeos.apps.bookeditor.ui.dialogs.BookDetailsDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.CheckDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.LinkDialog;
import eu.amadeos.apps.bookeditor.ui.dialogs.NewBookDialog;
import eu.amadeos.apps.bookeditor.ui.editor.ChapterDocument;
import eu.amadeos.apps.bookeditor.ui.editor.ChapterEditor;
import eu.amadeos.apps.bookeditor.ui.editor.EditorStyles;
import eu.amadeos.commons.chars.SpecialCharacters;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;
import eu.amadeos.commons.typeset.Hyphenators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * The Book Editor window: the outline of the book on the left, the chapter (or the cover, title page, contents
 * or pictures) in the middle, the preview on the right when it is on. Menus Book, Edit, Insert, Chapter, Format,
 * View, Options and Help; a toolbar with what is needed most; the status line with the words and whether the
 * book is ready.
 */
public final class MainWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    /** Words on a printed page, for the "about 160 pages" in the status line. */
    static final int WORDS_PER_PAGE = 250;
    static final int AUTOSAVE_MS = 120_000;

    private final Messages msg;
    private final LocaleSettings locales;
    private final EditorPreferences prefs;
    private final Backups backups;
    private final EpubCheckRunner epubcheck;
    private final DesktopLauncher launcher;
    private final ExecutorService background;
    private final Path documents;

    private Ask ask;
    private Runnable exitAction = () -> System.exit(0);
    private Supplier<Instant> clock = Instant::now;

    // the book
    private Book book;
    private Path file;
    /** LibreOffice for .doc and .rtf files, looked for when such a file comes; tests give their own. */
    private Supplier<Optional<LibreOfficeConverter>> office = LibreOfficeConverter::find;
    /** Where a book taken over from another program would be saved, until it has a file. */
    private Path suggestedFile;
    private boolean dirty;
    private OutlinePanel.Kind shownKind;
    private String chapterId;
    private final Map<String, BufferedImage> images = new HashMap<>();

    // the window
    private final ChapterEditor editor = new ChapterEditor();
    private final JScrollPane editorScroll = new JScrollPane(editor);
    private final OutlinePanel outline;
    private final JPanel centre = new JPanel(new BorderLayout());
    private final JSplitPane split;
    private final PreviewPanel preview;
    private final JLabel status = new JLabel(" ");
    private final JLabel readiness = new JLabel(" ");
    private final JComboBox<ParaStyle> styleBox = new JComboBox<>(ParaStyle.values());
    private final Map<Mark, JToggleButton> markButtons = new EnumMap<>(Mark.class);
    private final Map<ParaStyle, JRadioButtonMenuItem> styleItems = new EnumMap<>(ParaStyle.class);
    private final Map<Look, JRadioButtonMenuItem> lookItems = new EnumMap<>(Look.class);
    private final JToggleButton previewButton;
    private final JCheckBoxMenuItem previewItem;
    private final JCheckBoxMenuItem autosaveItem;
    private final JCheckBoxMenuItem checkWhileWritingItem;
    private final JCheckBoxMenuItem countedItem;
    private final JMenu recentMenu;
    private final List<Action> bookActions = new ArrayList<>();
    private final List<Action> chapterActions = new ArrayList<>();
    private final Timer liveTimer;
    private final Timer autosaveTimer;
    private CheckDialog checkDialog;
    private boolean updating;
    private boolean loading;

    // set while the Book Details dialog is open: a new cover the writer chose there
    private Resource pendingCover;

    @Inject
    public MainWindow(Messages msg, LocaleSettings locales, EditorPreferences prefs, Backups backups, EpubCheckRunner epubcheck,
                      Hyphenators hyphenators, DesktopLauncher launcher, ExecutorService background, XdgDirectories xdg) {
        super(msg.get("window.title"));
        this.msg = msg;
        this.locales = locales;
        this.prefs = prefs;
        this.backups = backups;
        this.epubcheck = epubcheck;
        this.launcher = launcher;
        this.background = background;
        this.documents = xdg == null ? Path.of(System.getProperty("user.home")) : xdg.documents();
        this.ask = new SwingAsk(this, msg);
        setName("book-editor");
        java.net.URL icon = getClass().getResource("/icons/amade-bookeditor.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        outline = new OutlinePanel(msg, this::open, this::addChapter, this::moveChapter, this::deleteChapter, this::chapterMenu);
        preview = new PreviewPanel(msg, hyphenators, background);
        preview.setImages(this::image);
        preview.setDevice(PreviewPanel.Device.of(prefs.get("preview.device", "ereader")));
        preview.setColours(PreviewPanel.Colours.of(prefs.get("preview.colours", "day")));
        preview.setOnChange(() -> {
            prefs.set("preview.device", preview.device().key());
            prefs.set("preview.colours", preview.colours().key());
        });

        editor.setName("chapter-editor");
        editor.setObjectHandler(new ChapterEditor.ObjectHandler() {
            @Override
            public void editPicture(int offset, Block.Picture picture) {
                MainWindow.this.editPicture(offset, picture);
            }

            @Override
            public void editFootnote(int offset, String text) {
                MainWindow.this.editFootnote(offset, text);
            }

            @Override
            public void editKept(int offset, Block.Kept kept) {
                MainWindow.this.editKept(offset, kept);
            }
        });
        ChapterDocument.setKeptTexts(kind -> msg.get("kept." + kind.key()), msg.get("kept.hint"));
        editor.setLinkTitles(this::linkTitle);
        editor.addCaretListener(e -> showCaretState());
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.getVerticalScrollBar().setUnitIncrement(18);
        editorScroll.getViewport().setBackground(Color.WHITE);
        editorScroll.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitPage();
            }
        });

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centre, null);
        split.setResizeWeight(1.0);
        split.setBorder(BorderFactory.createEmptyBorder());

        liveTimer = new Timer(500, e -> afterTyping());
        liveTimer.setRepeats(false);
        autosaveTimer = new Timer(AUTOSAVE_MS, e -> autosave());
        autosaveTimer.start();

        previewButton = new JToggleButton(msg.get("toolbar.preview"));
        previewItem = new JCheckBoxMenuItem();
        autosaveItem = new JCheckBoxMenuItem();
        checkWhileWritingItem = new JCheckBoxMenuItem();
        countedItem = new JCheckBoxMenuItem();
        recentMenu = Mnemonics.apply(new JMenu(), msg.get("menu.book.recent"));
        recentMenu.setName("menu.book.recent");

        setJMenuBar(menuBar());
        JPanel root = new JPanel(new BorderLayout());
        root.add(toolBar(), BorderLayout.NORTH);
        root.add(outline, BorderLayout.WEST);
        root.add(split, BorderLayout.CENTER);
        root.add(statusBar(), BorderLayout.SOUTH);
        setContentPane(root);
        SpecialCharacters.installKey(this, msg.locale());
        setPreview(prefs.flag("preview", false));
        showWelcome();
        setSize(new Dimension(1180, 760));
        setLocationByPlatform(true);
    }

    // ================================================================ for tests and the bootstrap

    public void setAsk(Ask ask) {
        this.ask = ask;
    }

    public void setExitAction(Runnable exit) {
        this.exitAction = exit;
    }

    public void setClock(Supplier<Instant> clock) {
        this.clock = clock;
    }

    public void setOffice(Supplier<Optional<LibreOfficeConverter>> office) {
        this.office = office;
    }

    /** Opens the books named on the command line, or shows the welcome page. */
    public void start(List<String> args) {
        for (String a : args) {
            if (a.startsWith("-")) continue;
            Path p = Path.of(a);
            if (Files.isRegularFile(p)) {
                openFile(p);
                return;
            }
        }
    }

    public Book book() {
        return book;
    }

    public Path file() {
        return file;
    }

    public boolean dirty() {
        return dirty;
    }

    public ChapterEditor editor() {
        return editor;
    }

    public OutlinePanel outline() {
        return outline;
    }

    public String currentChapterId() {
        return shownKind == OutlinePanel.Kind.CHAPTER || shownKind == OutlinePanel.Kind.DEDICATION ? chapterId : null;
    }

    public OutlinePanel.Kind shownKind() {
        return shownKind;
    }

    public String statusText() {
        return status.getText();
    }

    public String readinessText() {
        return readiness.getText();
    }

    public CheckDialog checkDialog() {
        return checkDialog;
    }

    public PreviewPanel preview() {
        return preview;
    }

    // ================================================================ menus and toolbar

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu bookMenu = menu(bar, "menu.book");
        bookMenu.add(item(action("menu.book.new", key(KeyEvent.VK_N, CTRL), StandardGlyph.NEW, this::newBook)));
        bookMenu.add(item(action("menu.book.open", key(KeyEvent.VK_O, CTRL), StandardGlyph.OPEN, this::open)));
        bookMenu.add(recentMenu);
        bookMenu.addSeparator();
        bookMenu.add(item(book(action("menu.book.save", key(KeyEvent.VK_S, CTRL), StandardGlyph.SAVE, this::save))));
        bookMenu.add(item(book(action("menu.book.save-as", key(KeyEvent.VK_S, CTRL | SHIFT), null, this::saveAs))));
        bookMenu.addSeparator();
        bookMenu.add(item(book(action("menu.book.details", key(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), null,
                () -> details(BookDetailsDialog.GENERAL)))));
        bookMenu.add(item(book(action("menu.book.cover", null, null, () -> outline.select(OutlinePanel.Kind.COVER, null)))));
        bookMenu.add(item(book(action("menu.book.check", key(KeyEvent.VK_F7, 0), null, this::checkBook))));
        bookMenu.addSeparator();
        bookMenu.add(item(action("menu.book.exit", key(KeyEvent.VK_Q, CTRL), null, this::exit)));
        bookMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                fillRecent();
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });

        JMenu edit = menu(bar, "menu.edit");
        edit.add(item(chapter(action("menu.edit.undo", key(KeyEvent.VK_Z, CTRL), StandardGlyph.UNDO, this::undo))));
        edit.add(item(chapter(action("menu.edit.redo", key(KeyEvent.VK_Y, CTRL), StandardGlyph.REDO, this::redo))));
        edit.addSeparator();
        edit.add(item(chapter(textAction("menu.edit.cut", key(KeyEvent.VK_X, CTRL), DefaultEditorKit.cutAction))));
        edit.add(item(chapter(textAction("menu.edit.copy", key(KeyEvent.VK_C, CTRL), DefaultEditorKit.copyAction))));
        edit.add(item(chapter(textAction("menu.edit.paste", key(KeyEvent.VK_V, CTRL), DefaultEditorKit.pasteAction))));
        edit.addSeparator();
        edit.add(item(chapter(textAction("menu.edit.select-all", key(KeyEvent.VK_A, CTRL), DefaultEditorKit.selectAllAction))));

        JMenu insert = menu(bar, "menu.insert");
        insert.add(item(book(action("menu.insert.chapter", key(KeyEvent.VK_ENTER, CTRL), null, this::addChapter))));
        insert.addSeparator();
        insert.add(item(chapter(action("menu.insert.picture", null, null, this::insertPicture))));
        insert.add(item(chapter(action("menu.insert.footnote", key(KeyEvent.VK_F, CTRL | InputEvent.ALT_DOWN_MASK), null,
                this::insertFootnote))));
        insert.add(item(chapter(action("menu.insert.link", key(KeyEvent.VK_K, CTRL), null, this::editLink))));
        insert.add(item(chapter(action("menu.insert.scene-break", null, null, this::insertSceneBreak))));
        insert.addSeparator();
        insert.add(item(book(action("menu.insert.dedication", null, null, this::addDedication))));
        insert.addSeparator();
        JMenuItem special = SpecialCharacters.install(this, insert, msg.locale());
        if (special != null) special.setName("menu.insert.special");

        JMenu chapterMenu = menu(bar, "menu.chapter");
        chapterMenu.add(item(book(action("menu.chapter.new", null, null, this::addChapter))));
        chapterMenu.add(item(chapter(action("menu.chapter.split", null, null, this::splitChapter))));
        chapterMenu.add(item(chapter(action("menu.chapter.join", null, null, this::joinWithNext))));
        chapterMenu.addSeparator();
        chapterMenu.add(item(chapter(action("menu.chapter.up", key(KeyEvent.VK_UP, CTRL | SHIFT), null, () -> moveChapter(-1)))));
        chapterMenu.add(item(chapter(action("menu.chapter.down", key(KeyEvent.VK_DOWN, CTRL | SHIFT), null,
                () -> moveChapter(1)))));
        Mnemonics.apply(countedItem, msg.get("menu.chapter.counted"));
        countedItem.setName("menu.chapter.counted");
        countedItem.addActionListener(e -> setCounted(countedItem.isSelected()));
        chapterMenu.add(countedItem);
        chapterMenu.addSeparator();
        chapterMenu.add(item(chapter(action("menu.chapter.delete", null, null, this::deleteChapter))));

        JMenu format = menu(bar, "menu.format");
        ButtonGroup styles = new ButtonGroup();
        int[] keys = {KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3};
        int k = 0;
        for (ParaStyle s : ParaStyle.values()) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem();
            Mnemonics.apply(it, msg.get("style." + s.key()));
            it.setName("menu.format.style." + s.key());
            if (k < keys.length) it.setAccelerator(key(keys[k++], CTRL));
            it.addActionListener(e -> applyStyle(s));
            styles.add(it);
            styleItems.put(s, it);
            format.add(it);
            if (s == ParaStyle.SUBHEADING) format.addSeparator();
        }
        format.addSeparator();
        format.add(item(chapter(action("menu.format.bold", key(KeyEvent.VK_B, CTRL), null, () -> toggle(Mark.BOLD)))));
        format.add(item(chapter(action("menu.format.italic", key(KeyEvent.VK_I, CTRL), null, () -> toggle(Mark.ITALIC)))));
        format.add(item(chapter(action("menu.format.superscript", null, null, () -> toggle(Mark.SUPERSCRIPT)))));
        format.add(item(chapter(action("menu.format.subscript", null, null, () -> toggle(Mark.SUBSCRIPT)))));
        format.addSeparator();
        JMenu looks = Mnemonics.apply(new JMenu(), msg.get("menu.format.look"));
        looks.setName("menu.format.look");
        ButtonGroup lookGroup = new ButtonGroup();
        for (Look l : Look.values()) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(msg.get("look." + l.key()));
            it.setName("menu.format.look." + l.key());
            it.addActionListener(e -> setLook(l));
            lookGroup.add(it);
            lookItems.put(l, it);
            looks.add(it);
        }
        format.add(looks);

        JMenu view = menu(bar, "menu.view");
        Mnemonics.apply(previewItem, msg.get("menu.view.preview"));
        previewItem.setName("menu.view.preview");
        previewItem.setAccelerator(key(KeyEvent.VK_F8, 0));
        previewItem.addActionListener(e -> setPreview(previewItem.isSelected()));
        view.add(previewItem);

        JMenu options = menu(bar, "menu.options");
        Mnemonics.apply(autosaveItem, msg.get("menu.options.autosave"));
        autosaveItem.setName("menu.options.autosave");
        autosaveItem.setSelected(prefs.flag("autosave", true));
        autosaveItem.addActionListener(e -> prefs.set("autosave", autosaveItem.isSelected()));
        options.add(autosaveItem);
        Mnemonics.apply(checkWhileWritingItem, msg.get("menu.options.check-while-writing"));
        checkWhileWritingItem.setName("menu.options.check-while-writing");
        checkWhileWritingItem.setSelected(prefs.flag("check-while-writing", true));
        checkWhileWritingItem.addActionListener(e -> setCheckWhileWriting(checkWhileWritingItem.isSelected()));
        options.add(checkWhileWritingItem);
        if (locales != null) options.add(languageMenu());

        JMenu help = menu(bar, "menu.help");
        help.add(item(action("menu.help.about", null, null, this::about)));
        return bar;
    }

    private JToolBar toolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setName("toolbar");
        tb.add(ToolButtons.button(actionNamed("menu.book.new")));
        tb.add(ToolButtons.button(actionNamed("menu.book.open")));
        tb.add(ToolButtons.button(actionNamed("menu.book.save")));
        tb.addSeparator();
        tb.add(ToolButtons.button(actionNamed("menu.edit.undo")));
        tb.add(ToolButtons.button(actionNamed("menu.edit.redo")));
        tb.addSeparator();
        styleBox.setName("toolbar.style");
        styleBox.setRenderer(Renderers.keyed(msg, "style.", ParaStyle::key));
        styleBox.setPrototypeDisplayValue(ParaStyle.CHAPTER_TITLE);
        styleBox.setMaximumSize(styleBox.getPreferredSize());
        styleBox.setFocusable(false);
        styleBox.setToolTipText(msg.get("toolbar.style.tip"));
        styleBox.addActionListener(e -> {
            if (!updating && styleBox.getSelectedItem() instanceof ParaStyle s) applyStyle(s);
        });
        tb.add(styleBox);
        tb.add(Box.createHorizontalStrut(4));
        tb.add(markButton(Mark.BOLD, "B", Font.BOLD));
        tb.add(markButton(Mark.ITALIC, "I", Font.ITALIC));
        tb.addSeparator();
        tb.add(textButton("toolbar.picture", "menu.insert.picture"));
        tb.add(textButton("toolbar.footnote", "menu.insert.footnote"));
        tb.add(textButton("toolbar.link", "menu.insert.link"));
        tb.add(textButton("toolbar.scene-break", "menu.insert.scene-break"));
        tb.add(Box.createHorizontalGlue());
        previewButton.setName("toolbar.preview");
        previewButton.setFocusable(false);
        previewButton.setToolTipText(msg.get("toolbar.preview.tip"));
        previewButton.addActionListener(e -> setPreview(previewButton.isSelected()));
        tb.add(previewButton);
        tb.add(Box.createHorizontalStrut(4));
        JButton check = new JButton(actionNamed("menu.book.check"));
        check.setText(msg.get("toolbar.check"));
        check.setName("toolbar.check");
        check.setFocusable(false);
        tb.add(check);
        tb.add(Box.createHorizontalStrut(6));
        return tb;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        status.setName("status");
        readiness.setName("readiness");
        readiness.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        readiness.setToolTipText(msg.get("status.readiness.tip"));
        readiness.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (book != null) checkBook();
            }
        });
        bar.add(status, BorderLayout.WEST);
        bar.add(readiness, BorderLayout.EAST);
        return bar;
    }

    private JToggleButton markButton(Mark mark, String label, int style) {
        JToggleButton b = new JToggleButton(label);
        b.setFont(b.getFont().deriveFont(style, 14f));
        b.setName("toolbar." + mark.name().toLowerCase(Locale.ROOT));
        b.setFocusable(false);
        b.setToolTipText(msg.get("menu.format." + mark.name().toLowerCase(Locale.ROOT)).replace("&", ""));
        b.addActionListener(e -> toggle(mark));
        markButtons.put(mark, b);
        chapterActions.add(new ButtonEnabler(b));
        return b;
    }

    private JButton textButton(String key, String actionKey) {
        Action a = actionNamed(actionKey);
        JButton b = new JButton(a);
        b.setText(msg.get(key));
        b.setName(key);
        b.setFocusable(false);
        b.setToolTipText(msg.get(key + ".tip"));
        return b;
    }

    /** Lets a plain button follow the chapter actions' enabled state. */
    private static final class ButtonEnabler extends AbstractAction {
        private final JComponent target;

        ButtonEnabler(JComponent target) {
            this.target = target;
        }

        @Override
        public void setEnabled(boolean on) {
            super.setEnabled(on);
            target.setEnabled(on);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        }
    }

    private final Map<String, Action> actions = new HashMap<>();

    private Action actionNamed(String key) {
        return actions.get(key);
    }

    private Action action(String key, KeyStroke stroke, StandardGlyph glyph, Runnable run) {
        Action a = new AbstractAction(msg.get(key)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        a.putValue(Action.ACTION_COMMAND_KEY, key);
        if (stroke != null) a.putValue(Action.ACCELERATOR_KEY, stroke);
        if (glyph != null) a.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        a.putValue(Action.SHORT_DESCRIPTION, msg.get(key).replace("&", "").replace("…", ""));
        actions.put(key, a);
        return a;
    }

    private Action textAction(String key, KeyStroke stroke, String editorAction) {
        Action a = action(key, stroke, null, () -> {
            Action real = editor.getActionMap().get(editorAction);
            if (real != null) real.actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, editorAction));
        });
        return a;
    }

    private Action book(Action a) {
        bookActions.add(a);
        return a;
    }

    private Action chapter(Action a) {
        chapterActions.add(a);
        return a;
    }

    private JMenuItem item(Action a) {
        JMenuItem it = new JMenuItem(a);
        it.setIcon(null);
        Mnemonics.apply(it, msg.get((String) a.getValue(Action.ACTION_COMMAND_KEY)));
        it.setName((String) a.getValue(Action.ACTION_COMMAND_KEY));
        return it;
    }

    private JMenu menu(JMenuBar bar, String key) {
        JMenu m = Mnemonics.apply(new JMenu(), msg.get(key));
        m.setName(key);
        bar.add(m);
        return m;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu languageMenu() {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            ask.error(msg.format("error.language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) ask.info(Messages.commons(msg.locale()).format("language.restart", "Book Editor"));
    }

    private void fillRecent() {
        recentMenu.removeAll();
        List<Path> recent = prefs.recentBooks();
        for (Path p : recent) {
            JMenuItem it = new JMenuItem(p.getFileName().toString());
            it.setToolTipText(p.toString());
            it.addActionListener(e -> openRecent(p));
            recentMenu.add(it);
        }
        recentMenu.setEnabled(!recent.isEmpty());
    }

    private JPopupMenu chapterMenu() {
        JPopupMenu m = new JPopupMenu();
        for (String key : List.of("menu.chapter.new", "menu.chapter.join", "menu.chapter.up", "menu.chapter.down",
                "menu.chapter.delete")) {
            m.add(item(actionNamed(key)));
        }
        JCheckBoxMenuItem counted = new JCheckBoxMenuItem(msg.get("menu.chapter.counted").replace("&", ""),
                countedItem.isSelected());
        counted.addActionListener(e -> setCounted(counted.isSelected()));
        m.add(counted);
        return m;
    }

    // ================================================================ showing things

    private void enableActions() {
        boolean open = book != null;
        boolean inChapter = open && currentChapterId() != null;
        for (Action a : bookActions) a.setEnabled(open);
        for (Action a : chapterActions) a.setEnabled(inChapter);
        styleBox.setEnabled(inChapter);
        countedItem.setEnabled(inChapter && shownKind == OutlinePanel.Kind.CHAPTER);
        previewButton.setEnabled(open);
        previewItem.setEnabled(open);
        for (JRadioButtonMenuItem it : styleItems.values()) it.setEnabled(inChapter);
        for (JRadioButtonMenuItem it : lookItems.values()) it.setEnabled(open);
        if (open) {
            JRadioButtonMenuItem it = lookItems.get(book.look());
            if (it != null) it.setSelected(true);
        }
    }

    private void showWelcome() {
        shownKind = null;
        chapterId = null;
        outline.setItems(List.of(), null, null);
        setCentre(Pages.welcome(msg, prefs.recentBooks(), this::newBook, this::open, this::openRecent));
        status.setText(" ");
        readiness.setText(" ");
        updateTitle();
        enableActions();
    }

    private void setCentre(JComponent c) {
        centre.removeAll();
        centre.add(c, BorderLayout.CENTER);
        centre.revalidate();
        centre.repaint();
    }

    private void fitPage() {
        int w = editorScroll.getViewport().getWidth();
        int side = Math.max(40, (w - 720) / 2);
        java.awt.Insets m = editor.getMargin();
        if (m.left != side) {
            editor.setMargin(new java.awt.Insets(28, side, 40, side));
            editor.revalidate();
        }
    }

    private void updateTitle() {
        String name = file != null ? file.getFileName().toString()
                : book != null ? (book.info().title().isBlank() ? msg.get("outline.untitled") : book.info().title()) : "";
        setTitle(name.isEmpty() ? msg.get("window.title") : (dirty ? "*" : "") + name + " – " + msg.get("window.title"));
    }

    private List<OutlinePanel.Item> items() {
        List<OutlinePanel.Item> list = new ArrayList<>();
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.COVER, null, msg.get("outline.cover")));
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.TITLE_PAGE, null, msg.get("outline.title-page")));
        if (book.dedication().isPresent()) {
            list.add(new OutlinePanel.Item(OutlinePanel.Kind.DEDICATION, Book.DEDICATION_ID, msg.get("outline.dedication")));
        }
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.CONTENTS, null, msg.get("outline.contents")));
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.SEPARATOR, null, ""));
        for (Chapter c : book.chapters()) list.add(new OutlinePanel.Item(OutlinePanel.Kind.CHAPTER, c.id(), label(c)));
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.SEPARATOR, null, ""));
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.PICTURES, null,
                msg.format("outline.pictures", book.pictures().size())));
        list.add(new OutlinePanel.Item(OutlinePanel.Kind.DETAILS, null, msg.get("outline.details")));
        return list;
    }

    /** "3  The Storm", or the title alone for a chapter that is not counted. */
    String label(Chapter c) {
        if (c.id().equals(Book.DEDICATION_ID)) return msg.get("outline.dedication");
        String title = c.title().isEmpty() ? msg.get("outline.untitled") : c.title();
        int n = book.numberOf(c.id());
        return n > 0 ? n + "  " + title : title;
    }

    private String linkTitle(String link) {
        if (book == null) return link;
        return Link.chapterId(link).flatMap(book::chapter).map(this::label).orElse(link);
    }

    private void refreshOutline() {
        OutlinePanel.Kind kind = shownKind == null ? OutlinePanel.Kind.CHAPTER : shownKind;
        outline.setItems(items(), kind, kind == OutlinePanel.Kind.CHAPTER || kind == OutlinePanel.Kind.DEDICATION ? chapterId : null);
    }

    /** Opens what an outline line stands for. */
    private void open(OutlinePanel.Item item) {
        if (book == null) return;
        commit();
        switch (item.kind()) {
            case CHAPTER, DEDICATION -> showChapter(item.chapterId());
            case COVER -> showPage(OutlinePanel.Kind.COVER, Pages.cover(msg, coverImage(), this::chooseCover, this::makeCover,
                    this::removeCover));
            case TITLE_PAGE -> showPage(OutlinePanel.Kind.TITLE_PAGE, Pages.titlePage(msg, book.info(),
                    () -> details(BookDetailsDialog.GENERAL)));
            case CONTENTS -> showPage(OutlinePanel.Kind.CONTENTS, Pages.contents(msg, book, this::label,
                    id -> outline.select(OutlinePanel.Kind.CHAPTER, id)));
            case PICTURES -> showPage(OutlinePanel.Kind.PICTURES, Pages.pictures(msg, book, this::image, this::label,
                    use -> editPictureOf(use.chapter().id(), use.picture().resource())));
            case DETAILS -> {
                OutlinePanel.Kind before = shownKind;
                String beforeId = chapterId;
                SwingUtilities.invokeLater(() -> {
                    if (before != null) outline.mark(before, beforeId);
                    details(BookDetailsDialog.GENERAL);
                });
            }
            case SEPARATOR -> {
            }
        }
    }

    private void showPage(OutlinePanel.Kind kind, JComponent page) {
        shownKind = kind;
        chapterId = null;
        setCentre(page);
        updateStatus();
        enableActions();
    }

    private void showChapter(String id) {
        Optional<Chapter> c = book.chapter(id);
        if (c.isEmpty()) return;
        loading = true;
        try {
            // An empty text paragraph after a closing title, picture or scene break, so that writing goes on
            // below it; saving drops it again when it stays empty.
            Chapter shown = c.get();
            boolean endsInText = !shown.blocks().isEmpty() && shown.blocks().getLast() instanceof Block.Paragraph last
                    && last.style() == ParaStyle.TEXT;
            if (!endsInText) {
                List<Block> blocks = new ArrayList<>(shown.blocks());
                blocks.add(new Block.Paragraph(ParaStyle.TEXT, ""));
                shown = shown.withBlocks(blocks);
            }
            ChapterDocument doc = ChapterDocument.fromChapter(shown, this::image);
            editor.setChapter(doc);
            doc.addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    // Attributes only, and also when Swing updates its styles later: the comparison in
                    // commit() decides whether the chapter really changed.
                    if (!loading) liveTimer.restart();
                }
            });
        } finally {
            loading = false;
        }
        shownKind = id.equals(Book.DEDICATION_ID) ? OutlinePanel.Kind.DEDICATION : OutlinePanel.Kind.CHAPTER;
        chapterId = id;
        outline.mark(shownKind, id);
        setCentre(editorScroll);
        fitPage();
        updating = true;
        countedItem.setSelected(c.get().numbered());
        updating = false;
        enableActions();
        showCaretState();
        updateStatus();
        updatePreview(true);
        SwingUtilities.invokeLater(editor::requestFocusInWindow);
    }

    /** Shows the paragraph style and marks at the caret in the toolbar and Format menu. */
    private void showCaretState() {
        if (currentChapterId() == null) return;
        updating = true;
        try {
            ParaStyle s = editor.currentStyle();
            styleBox.setSelectedItem(s);
            JRadioButtonMenuItem it = styleItems.get(s);
            if (it != null) it.setSelected(true);
            for (Map.Entry<Mark, JToggleButton> e : markButtons.entrySet()) e.getValue().setSelected(editor.has(e.getKey()));
        } finally {
            updating = false;
        }
    }

    private void changed() {
        if (loading) return;
        setDirty();
        liveTimer.restart();
    }

    private void setDirty() {
        if (!dirty) {
            dirty = true;
            updateTitle();
        }
    }

    /** A moment after typing stops: the chapter goes into the book, the outline, status and preview follow. */
    private void afterTyping() {
        if (book == null) return;
        String before = currentChapterId() == null ? null : book.chapter(currentChapterId()).map(Chapter::title).orElse("");
        commit();
        String after = currentChapterId() == null ? null : book.chapter(currentChapterId()).map(Chapter::title).orElse("");
        if (before != null && !before.equals(after)) refreshOutline();
        updateStatus();
        updatePreview(false);
    }

    /** Takes the text of the chapter being edited into the book. */
    void commit() {
        String id = currentChapterId();
        if (book == null || id == null) return;
        Optional<Chapter> old = book.chapter(id);
        if (old.isEmpty()) return;
        Chapter now = old.get().withBlocks(editor.chapter().toBlocks());
        if (!now.equals(old.get())) {
            book.replace(now);
            setDirty();
        }
    }

    private void updatePreview(boolean now) {
        if (book == null || !previewButton.isSelected()) return;
        Chapter c = currentChapterId() != null ? book.chapter(currentChapterId()).orElse(null)
                : book.chapters().isEmpty() ? null : book.chapters().getFirst();
        if (c != null) preview.show(book, c, now);
    }

    private void updateStatus() {
        if (book == null) return;
        int words = book.words();
        int pages = Math.max(1, (words + WORDS_PER_PAGE / 2) / WORDS_PER_PAGE);
        String id = currentChapterId();
        int index = -1;
        List<Chapter> chapters = book.chapters();
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).id().equals(id)) index = i;
        if (index >= 0) {
            status.setText(msg.format("status.chapter", index + 1, chapters.size(), chapters.get(index).words(), words, pages));
        } else {
            status.setText(msg.format("status.book", chapters.size(), words, pages));
        }
        updateReadiness();
    }

    private void updateReadiness() {
        if (book == null || !checkWhileWritingItem.isSelected()) {
            readiness.setText(" ");
            return;
        }
        List<Finding> findings = BookCheck.check(book);
        long problems = findings.stream().filter(f -> f.kind().severity() == Finding.Severity.PROBLEM).count();
        if (problems == 0) {
            readiness.setText("✓ " + msg.get("status.ready"));
            readiness.setForeground(CheckDialog.OK);
        } else {
            readiness.setText("! " + msg.format("status.not-ready", problems));
            readiness.setForeground(CheckDialog.PROBLEM.darker());
        }
    }

    // ================================================================ pictures

    /** A picture of the book, decoded once. */
    BufferedImage image(String name) {
        if (book == null || name == null) return null;
        BufferedImage cached = images.get(name);
        if (cached != null) return cached;
        BufferedImage img = book.resource(name).flatMap(r -> Pictures.read(r.data())).orElse(null);
        if (img != null) images.put(name, img);
        return img;
    }

    private BufferedImage coverImage() {
        return book.cover().map(this::image).orElse(null);
    }

    /** Reads a picture file into a resource the book can carry (JPEG, PNG, GIF, SVG; others become PNG). */
    Optional<Resource> readPicture(Path p) {
        try {
            byte[] data = Files.readAllBytes(p);
            String type = Resource.mediaTypeOf(p.getFileName().toString());
            String name = p.getFileName().toString();
            Optional<BufferedImage> img = Pictures.read(data);
            if (!type.equals("image/svg+xml")) {
                if (img.isEmpty()) {
                    ask.error(msg.format("error.picture", p.getFileName()));
                    return Optional.empty();
                }
                if (!type.equals("image/jpeg") && !type.equals("image/png") && !type.equals("image/gif")) {
                    data = Pictures.png(img.get());
                    type = "image/png";
                    name = name.replaceFirst("\\.[^.]+$", "") + ".png";
                }
            }
            Resource r = new Resource(Resource.safeName(name), type, data);
            if (r.size() > BookCheck.BIG_PICTURE_BYTES) r = Pictures.shrink(r).orElse(r);
            return Optional.of(r);
        } catch (IOException e) {
            ask.error(msg.format("error.picture", p.getFileName()));
            return Optional.empty();
        }
    }

    private Path pictureFolder() {
        return Path.of(prefs.get("picture-folder", documents.toString()));
    }

    private Optional<Resource> askPicture() {
        Optional<Path> p = ask.pictureFile(pictureFolder());
        if (p.isEmpty()) return Optional.empty();
        if (p.get().getParent() != null) prefs.set("picture-folder", p.get().getParent());
        return readPicture(p.get());
    }

    private void insertPicture() {
        if (currentChapterId() == null) return;
        Optional<Resource> r = askPicture();
        if (r.isEmpty()) return;
        Resource res = r.get();
        String name = book.addResource(res.name(), res.mediaType(), res.data());
        images.remove(name);
        Block.Picture start = new Block.Picture(name, "", "", PictureSize.MEDIUM);
        Optional<Block.Picture> answer = ask.picture(start, image(name));
        if (answer.isEmpty()) {
            book.dropUnused();
            return;
        }
        editor.insertBlock(answer.get());
        afterChange();
    }

    private void editPicture(int offset, Block.Picture picture) {
        ask.picture(picture, image(picture.resource())).ifPresent(p -> {
            editor.replacePicture(offset, p);
            afterChange();
        });
    }

    /** Opens the chapter with the picture and its settings (from the Pictures page and Check Book). */
    private void editPictureOf(String chapter, String resource) {
        outline.select(chapter.equals(Book.DEDICATION_ID) ? OutlinePanel.Kind.DEDICATION : OutlinePanel.Kind.CHAPTER, chapter);
        if (!chapter.equals(currentChapterId())) showChapter(chapter);
        int offset = findObject(EditorStyles.PICTURE, v -> v instanceof Block.Picture p && p.resource().equals(resource));
        if (offset < 0) return;
        editor.setCaretPosition(offset);
        editPicture(offset, (Block.Picture) editor.chapter().objectAt(offset).getAttributes().getAttribute(EditorStyles.PICTURE));
    }

    private int findObject(Object key, java.util.function.Predicate<Object> test) {
        ChapterDocument doc = editor.chapter();
        for (int i = 0; i < doc.getLength(); i++) {
            Element leaf = doc.objectAt(i);
            if (leaf != null && leaf.getAttributes().isDefined(key) && test.test(leaf.getAttributes().getAttribute(key))) {
                return i;
            }
        }
        return -1;
    }

    // --8<-- [start:kept]
    /** Kept content of a book made elsewhere: after asking, it becomes plain paragraphs. */
    private void editKept(int offset, Block.Kept kept) {
        String id = currentChapterId();
        if (id == null || !ask.confirm(msg.format("kept.to-text", msg.get("kept." + kept.kind().key())))) return;
        int before = 0;
        ChapterDocument doc = editor.chapter();
        for (int i = 0; i < offset; i++) {
            Element leaf = doc.objectAt(i);
            if (leaf != null && leaf.getAttributes().isDefined(EditorStyles.KEPT)) before++;
        }
        commit();
        Chapter c = book.chapter(id).orElseThrow();
        List<Block> blocks = new ArrayList<>();
        int seen = 0;
        for (Block b : c.blocks()) {
            if (b instanceof Block.Kept k && seen++ == before) blocks.addAll(eu.amadeos.apps.bookeditor.domain.epub.KeptText.paragraphs(k));
            else blocks.add(b);
        }
        book.replace(c.withBlocks(blocks));
        book.dropUnused();
        showChapter(id);
        afterChange();
    }
    // --8<-- [end:kept]

    private void insertSceneBreak() {
        if (currentChapterId() == null) return;
        editor.insertBlock(new Block.SceneBreak());
        afterChange();
    }

    // ================================================================ footnotes and links

    private int footnotesBefore(int offset) {
        ChapterDocument doc = editor.chapter();
        int n = 0;
        for (int i = 0; i < offset && i < doc.getLength(); i++) {
            Element leaf = doc.objectAt(i);
            if (leaf != null && leaf.getAttributes().isDefined(EditorStyles.FOOTNOTE)) n++;
        }
        return n;
    }

    private void insertFootnote() {
        if (currentChapterId() == null) return;
        int number = footnotesBefore(editor.getSelectionStart()) + 1;
        ask.footnote(number, "").ifPresent(text -> {
            editor.insertFootnote(text);
            afterChange();
        });
    }

    private void editFootnote(int offset, String text) {
        ask.footnote(footnotesBefore(offset) + 1, text).ifPresent(t -> {
            editor.replaceFootnote(offset, t);
            afterChange();
        });
    }

    private void editLink() {
        if (currentChapterId() == null) return;
        commit();
        int[] range = editor.linkRange();
        String selected;
        try {
            selected = editor.chapter().getText(range[0], range[1] - range[0]).replace(ChapterDocument.OBJECT, "").strip();
        } catch (BadLocationException e) {
            selected = "";
        }
        List<LinkDialog.Target> targets = new ArrayList<>();
        for (Chapter c : book.chapters()) {
            if (!c.id().equals(currentChapterId())) targets.add(new LinkDialog.Target(c.id(), label(c)));
        }
        String current = editor.currentLink();
        String sel = selected;
        ask.link(targets, current, selected).ifPresent(a -> {
            if (range[0] == range[1] && a.target().isEmpty()) return;
            editor.setLink(range[0], range[1], a.target(), sel.isEmpty() ? a.text() : "");
            afterChange();
        });
    }

    /** After a change made through a dialog: into the book at once. */
    private void afterChange() {
        liveTimer.stop();
        commit();
        setDirty();
        refreshOutline();
        updateStatus();
        updatePreview(false);
        editor.requestFocusInWindow();
    }

    // ================================================================ styles

    private void applyStyle(ParaStyle s) {
        if (updating || currentChapterId() == null) return;
        editor.applyStyle(s);
        showCaretState();
        afterChange();
    }

    private void toggle(Mark mark) {
        if (currentChapterId() == null) return;
        editor.toggle(mark);
        showCaretState();
    }

    private void setLook(Look l) {
        if (book == null || book.look() == l) return;
        book.setLook(l);
        setDirty();
        updatePreview(true);
    }

    private void undo() {
        editor.undo();
        showCaretState();
    }

    private void redo() {
        editor.redo();
        showCaretState();
    }

    // ================================================================ chapters

    private int currentIndex() {
        List<Chapter> list = book.chapters();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(chapterId)) return i;
        return -1;
    }

    void addChapter() {
        if (book == null) return;
        commit();
        List<Chapter> list = new ArrayList<>(book.chapters());
        int at = shownKind == OutlinePanel.Kind.CHAPTER ? currentIndex() + 1 : list.size();
        Chapter c = Chapter.create(msg.get("chapter.new-title"));
        list.add(at, c);
        book.setChapters(list);
        setDirty();
        showChapterAfterChange(c.id());
        String title = c.title();
        editor.select(0, title.length());
    }

    private void showChapterAfterChange(String id) {
        shownKind = OutlinePanel.Kind.CHAPTER;
        chapterId = id;
        refreshOutline();
        showChapter(id);
    }

    void moveChapter(int delta) {
        if (book == null || shownKind != OutlinePanel.Kind.CHAPTER) return;
        commit();
        List<Chapter> list = new ArrayList<>(book.chapters());
        int i = currentIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= list.size()) return;
        Chapter c = list.remove(i);
        list.add(j, c);
        book.setChapters(list);
        setDirty();
        refreshOutline();
        updateStatus();
    }

    void deleteChapter() {
        if (book == null) return;
        commit();
        if (shownKind == OutlinePanel.Kind.DEDICATION) {
            if (!ask.confirm(msg.get("chapter.delete.dedication"))) return;
            book.setDedication(null);
            book.dropUnused();
            setDirty();
            shownKind = OutlinePanel.Kind.CHAPTER;
            showChapterAfterChange(book.chapters().getFirst().id());
            return;
        }
        if (shownKind != OutlinePanel.Kind.CHAPTER) return;
        List<Chapter> list = new ArrayList<>(book.chapters());
        if (list.size() <= 1) {
            ask.info(msg.get("chapter.delete.last"));
            return;
        }
        int i = currentIndex();
        Chapter c = list.get(i);
        if (c.words() > 0 && !ask.confirm(msg.format("chapter.delete.confirm", label(c), c.words()))) return;
        list.remove(i);
        book.setChapters(list);
        book.dropUnused();
        setDirty();
        showChapterAfterChange(list.get(Math.min(i, list.size() - 1)).id());
    }

    /** Splits the chapter at the caret's paragraph: from there on it is a new chapter. */
    void splitChapter() {
        if (shownKind != OutlinePanel.Kind.CHAPTER) return;
        commit();
        ChapterDocument doc = editor.chapter();
        int at = doc.getParagraphElement(editor.getCaretPosition()).getStartOffset();
        if (at == 0) {
            ask.info(msg.get("chapter.split.start"));
            return;
        }
        Chapter c = book.chapter(chapterId).orElseThrow();
        List<Block> all = c.blocks();
        int before;
        try {
            ChapterDocument head = ChapterDocument.fromChapter(c, n -> null);
            head.remove(at - 1, head.getLength() - (at - 1));
            before = head.toBlocks().size();
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        if (before >= all.size()) {
            ask.info(msg.get("chapter.split.end"));
            return;
        }
        List<Block> first = new ArrayList<>(all.subList(0, before));
        List<Block> rest = new ArrayList<>(all.subList(before, all.size()));
        while (rest.size() > 1 && rest.getFirst() instanceof Block.Paragraph p && p.isEmpty()) rest.removeFirst();
        if (rest.getFirst() instanceof Block.Paragraph p && p.style() != ParaStyle.CHAPTER_TITLE
                && (p.style().heading() || (p.style() == ParaStyle.TEXT && p.plainText().length() <= 80
                && p.content().stream().noneMatch(i -> i instanceof Inline.Footnote)))) {
            rest.set(0, new Block.Paragraph(ParaStyle.CHAPTER_TITLE, p.content()));
        } else if (!(rest.getFirst() instanceof Block.Paragraph p && p.style() == ParaStyle.CHAPTER_TITLE)) {
            rest.addFirst(new Block.Paragraph(ParaStyle.CHAPTER_TITLE, msg.get("chapter.new-title")));
        }
        List<Chapter> list = new ArrayList<>(book.chapters());
        int i = currentIndex();
        list.set(i, c.withBlocks(first));
        Chapter next = new Chapter(Chapter.newId(), c.numbered(), rest);
        list.add(i + 1, next);
        book.setChapters(list);
        setDirty();
        showChapterAfterChange(next.id());
    }

    /** Puts the next chapter at the end of this one; its title becomes a heading. */
    void joinWithNext() {
        if (shownKind != OutlinePanel.Kind.CHAPTER) return;
        commit();
        List<Chapter> list = new ArrayList<>(book.chapters());
        int i = currentIndex();
        if (i < 0 || i + 1 >= list.size()) {
            ask.info(msg.get("chapter.join.last"));
            return;
        }
        Chapter c = list.get(i), next = list.get(i + 1);
        List<Block> blocks = new ArrayList<>(c.blocks());
        for (Block b : next.blocks()) {
            blocks.add(b instanceof Block.Paragraph p && p.style() == ParaStyle.CHAPTER_TITLE
                    ? new Block.Paragraph(ParaStyle.HEADING, p.content()) : b);
        }
        list.set(i, c.withBlocks(blocks));
        list.remove(i + 1);
        book.setChapters(list);
        setDirty();
        showChapterAfterChange(c.id());
    }

    private void setCounted(boolean on) {
        if (updating || shownKind != OutlinePanel.Kind.CHAPTER) return;
        commit();
        book.chapter(chapterId).ifPresent(c -> book.replace(c.withNumbered(on)));
        countedItem.setSelected(on);
        setDirty();
        refreshOutline();
        updatePreview(true);
    }

    void addDedication() {
        if (book == null) return;
        commit();
        if (book.dedication().isEmpty()) {
            book.setDedication(new Chapter(Book.DEDICATION_ID, false,
                    List.of(new Block.Paragraph(ParaStyle.CENTERED, msg.get("dedication.placeholder")))));
            setDirty();
        }
        shownKind = OutlinePanel.Kind.DEDICATION;
        chapterId = Book.DEDICATION_ID;
        refreshOutline();
        showChapter(Book.DEDICATION_ID);
        editor.selectAll();
    }

    // ================================================================ book details and cover

    void details(int tab) {
        if (book == null) return;
        commit();
        pendingCover = null;
        BookDetailsDialog.CoverActions actions = new BookDetailsDialog.CoverActions() {
            @Override
            public Optional<BufferedImage> choosePicture() {
                Optional<Resource> r = askPicture();
                r.ifPresent(res -> pendingCover = res);
                return r.flatMap(res -> Pictures.read(res.data()));
            }

            @Override
            public Optional<BufferedImage> makeCover(BookInfo current) {
                Optional<BufferedImage> made = ask.makeCover(current, () -> askPicture().flatMap(res -> Pictures.read(res.data())));
                made.ifPresent(img -> pendingCover = madeCover(img));
                return made;
            }
        };
        Optional<BookInfo> answer = ask.details(book.info(), coverImage(), actions, tab);
        if (answer.isEmpty()) return;
        boolean changed = !answer.get().equals(book.info());
        book.setInfo(answer.get());
        if (pendingCover != null) {
            setCoverResource(pendingCover);
            changed = true;
        }
        pendingCover = null;
        if (changed) {
            setDirty();
            updateTitle();
            reopenShown();
        }
    }

    private Resource madeCover(BufferedImage img) {
        try {
            return new Resource("cover.jpg", "image/jpeg", CoverMaker.jpeg(img));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setCoverResource(Resource r) {
        String name = book.addResource(r.name().startsWith("cover") ? r.name() : "cover-" + r.name(), r.mediaType(), r.data());
        images.remove(name);
        book.setCover(name);
        setDirty();
    }

    private void chooseCover() {
        askPicture().ifPresent(r -> {
            setCoverResource(r);
            reopenShown();
        });
    }

    private void makeCover() {
        ask.makeCover(book.info(), () -> askPicture().flatMap(res -> Pictures.read(res.data()))).ifPresent(img -> {
            setCoverResource(madeCover(img));
            reopenShown();
        });
    }

    private void removeCover() {
        if (book.cover().isEmpty()) return;
        book.setCover("");
        setDirty();
        reopenShown();
    }

    /** Shows the current page again after the book changed under it. */
    private void reopenShown() {
        OutlinePanel.Kind kind = shownKind;
        String id = chapterId;
        refreshOutline();
        if (kind == null) return;
        if (kind == OutlinePanel.Kind.CHAPTER || kind == OutlinePanel.Kind.DEDICATION) {
            updateStatus();
            updatePreview(true);
        } else {
            open(new OutlinePanel.Item(kind, id, ""));
        }
    }

    // ================================================================ check book

    void checkBook() {
        if (book == null) return;
        commit();
        if (checkDialog == null) {
            checkDialog = new CheckDialog(this, msg, id -> book == null ? id : book.chapter(id)
                    .map(c -> c.title().isEmpty() ? label(c) : c.title()).orElse(id),
                    this::act, this::checkBook, checkWhileWritingItem.isSelected(), this::setCheckWhileWriting);
        }
        List<Finding> own = BookCheck.check(book);
        int footnotes = 0;
        List<Chapter> all = new ArrayList<>();
        book.dedication().ifPresent(all::add);
        all.addAll(book.chapters());
        for (Chapter c : all) {
            for (Block b : c.blocks()) {
                if (b instanceof Block.Paragraph p) {
                    for (Inline i : p.content()) if (i instanceof Inline.Footnote) footnotes++;
                }
            }
        }
        checkDialog.showFindings(own, msg.format("check.fine.text", book.chapters().size(), book.pictures().size(), footnotes));
        if (!epubcheck.available()) {
            checkDialog.showStandard(new CheckDialog.Standard.Missing());
        } else {
            checkDialog.showStandard(new CheckDialog.Standard.Running());
            EpubWriter writer = new EpubWriter(book, clock.get(), AmadeBookEditorApplication.version());
            Locale locale = msg.locale();
            CheckDialog dialog = checkDialog;
            byte[] epub;
            try (var out = new java.io.ByteArrayOutputStream()) {
                writer.write(out);
                epub = out.toByteArray();
            } catch (IOException e) {
                dialog.showStandard(new CheckDialog.Standard.Failed(e.getMessage()));
                epub = null;
            }
            if (epub != null) {
                byte[] bytes = epub;
                background.submit(() -> {
                    CheckDialog.Standard result;
                    Path temp = null;
                    try {
                        temp = Files.createTempFile("book-editor-check", ".epub");
                        try (OutputStream out = Files.newOutputStream(temp)) {
                            out.write(bytes);
                        }
                        EpubCheckRunner.Result r = epubcheck.check(temp, locale);
                        result = r.available() ? new CheckDialog.Standard.Done(r.version(), r.findings())
                                : new CheckDialog.Standard.Missing();
                    } catch (IOException e) {
                        result = new CheckDialog.Standard.Failed(String.valueOf(e.getMessage()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        result = new CheckDialog.Standard.Failed("");
                    } finally {
                        if (temp != null) {
                            try {
                                Files.deleteIfExists(temp);
                            } catch (IOException ignored) {
                                // a temporary file
                            }
                        }
                    }
                    CheckDialog.Standard shown = result;
                    SwingUtilities.invokeLater(() -> dialog.showStandard(shown));
                });
            }
        }
        if (!checkDialog.isVisible()) checkDialog.setVisible(true);
        updateReadiness();
    }

    /** What a finding's button in Check Book does. */
    void act(Finding f) {
        switch (f.kind().action()) {
            case DESCRIBE -> editPictureOf(f.chapterId(), f.detail());
            case SHOW -> {
                String id = f.chapterId();
                outline.select(id.equals(Book.DEDICATION_ID) ? OutlinePanel.Kind.DEDICATION : OutlinePanel.Kind.CHAPTER, id);
                if (!id.equals(currentChapterId())) showChapter(id);
                selectFinding(f);
                toFront();
            }
            case DETAILS -> details(BookDetailsDialog.GENERAL);
            case DESCRIPTION -> details(BookDetailsDialog.DESCRIPTION);
            case COVER -> {
                outline.select(OutlinePanel.Kind.COVER, null);
                toFront();
            }
            case SHRINK -> {
                book.resource(f.detail()).flatMap(Pictures::shrink).ifPresent(r -> {
                    book.putResource(r);
                    images.remove(r.name());
                    setDirty();
                });
                checkBook();
            }
            case DETAILS_TEXT, NONE -> {
            }
        }
    }

    private void selectFinding(Finding f) {
        ChapterDocument doc = editor.chapter();
        if (f.kind() == Finding.Kind.EMPTY_FOOTNOTE) {
            int offset = findObject(EditorStyles.FOOTNOTE, v -> "".equals(v));
            if (offset >= 0) {
                editor.setCaretPosition(offset);
                editFootnote(offset, "");
            }
            return;
        }
        if (f.detail().isEmpty()) return;
        try {
            String text = doc.getText(0, doc.getLength());
            int i = text.indexOf(f.detail());
            if (i < 0) {
                // a link's address: find the text carrying it
                for (int pos = 0; pos < doc.getLength(); pos++) {
                    if (f.detail().equals(ChapterDocument.link(doc.getCharacterElement(pos).getAttributes()))) {
                        i = pos;
                        break;
                    }
                }
                if (i >= 0) {
                    editor.setCaretPosition(i + 1);
                    int[] r = editor.linkRange();
                    editor.select(r[0], r[1]);
                }
                return;
            }
            editor.select(i, i + f.detail().length());
        } catch (BadLocationException ignored) {
            // nothing to select
        }
    }

    private void setCheckWhileWriting(boolean on) {
        prefs.set("check-while-writing", on);
        checkWhileWritingItem.setSelected(on);
        updateReadiness();
    }

    // ================================================================ files

    private Path folder() {
        Path last = Path.of(prefs.get("folder", documents.toString()));
        return Files.isDirectory(last) ? last : documents;
    }

    void newBook() {
        if (!closeCurrent()) return;
        Optional<NewBookDialog.Choice> choice = ask.newBook(prefs.get("author", ""), defaultLanguage(), folder());
        if (choice.isEmpty()) return;
        NewBookDialog.Choice c = choice.get();
        if (!c.author().isBlank()) prefs.set("author", c.author());
        Book b = Book.create(BookInfo.create(c.title(), c.author(), c.language()), c.look(), msg.get("chapter.new-title"));
        if (c.source().isPresent()) {
            try {
                TextImport.Result r = BookImport.read(c.source().get(), b, office);
                if (!r.chapters().isEmpty()) b.setChapters(r.chapters());
                if (!r.missingPictures().isEmpty()) {
                    ask.info(msg.format("import.missing-pictures", String.join(", ", r.missingPictures())));
                }
            } catch (BookImport.NeedsLibreOfficeException e) {
                ask.error(msg.format("import.needs-libreoffice", c.source().get().getFileName()));
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not import {}", c.source().get(), e);
                ask.error(msg.format("error.import", c.source().get().getFileName(), String.valueOf(e.getMessage())));
            }
        }
        setBook(b, null);
        dirty = true;
        updateTitle();
    }

    private String defaultLanguage() {
        String lang = msg.locale().getLanguage();
        return lang.isEmpty() ? "en" : lang;
    }

    void open() {
        if (!closeCurrent()) return;
        ask.openBook(folder()).ifPresent(this::load);
    }

    private void openRecent(Path p) {
        if (!closeCurrent()) return;
        load(p);
    }

    /** Opens a book, after asking about unsaved changes. */
    public void openFile(Path p) {
        if (!closeCurrent()) return;
        load(p);
    }

    private void load(Path p) {
        try {
            Book b = EpubReader.read(p);
            setBook(b, p);
            prefs.addRecent(p);
            if (p.toAbsolutePath().getParent() != null) prefs.set("folder", p.toAbsolutePath().getParent());
        } catch (EpubReader.NotEditableException e) {
            if (e.epub()) {
                switch (ask.foreign(msg.format("open.not-editable", p.getFileName()))) {
                    case EDIT_COPY -> takeOver(p);
                    case READ -> {
                        try {
                            launcher.open(p);
                        } catch (IOException ex) {
                            ask.error(msg.format("error.open", p.getFileName(), ex.getMessage()));
                        }
                    }
                    case CANCEL -> {
                    }
                }
            } else {
                ask.error(msg.format("open.not-epub", p.getFileName()));
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not open {}", p, e);
            ask.error(msg.format("error.open", p.getFileName(), String.valueOf(e.getMessage())));
        }
    }

    // --8<-- [start:take-over]
    /**
     * Takes over a book made by another program into a new book. It has no file yet, so saving asks where; the
     * suggested name ends in "(edited)", and the original stays as it was.
     */
    private void takeOver(Path p) {
        try {
            ForeignEpubReader.Result r = ForeignEpubReader.read(p);
            setBook(r.book(), null);
            Path folder = p.toAbsolutePath().getParent();
            if (folder != null) prefs.set("folder", folder);
            String name = p.getFileName().toString().replaceFirst("(?i)\\.epub$", "");
            suggestedFile = (folder == null ? Path.of(".") : folder).resolve(msg.format("open.copy-name", name) + ".epub");
            dirty = true;
            updateTitle();
            StringBuilder text = new StringBuilder(msg.format("open.taken-over", r.book().chapters().size()));
            if (r.kept() > 0) text.append("\n\n").append(msg.format("open.taken-over.kept", r.kept()));
            if (r.dropped() > 0) text.append("\n\n").append(msg.format("open.taken-over.dropped", r.dropped()));
            ask.info(text.toString());
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not take over {}", p, e);
            ask.error(msg.format("error.open", p.getFileName(), String.valueOf(e.getMessage())));
        }
    }
    // --8<-- [end:take-over]

    private void setBook(Book b, Path f) {
        book = b;
        file = f;
        suggestedFile = null;
        dirty = false;
        images.clear();
        if (checkDialog != null) {
            checkDialog.dispose();
            checkDialog = null;
        }
        shownKind = OutlinePanel.Kind.CHAPTER;
        String first = b.chapters().isEmpty() ? null : b.chapters().getFirst().id();
        chapterId = first;
        refreshOutline();
        if (first != null) showChapter(first);
        else open(new OutlinePanel.Item(OutlinePanel.Kind.TITLE_PAGE, null, ""));
        updateTitle();
        enableActions();
    }

    /** Saves to the book's file, or asks where for a new book. */
    boolean save() {
        if (book == null) return true;
        if (file == null) return saveAs();
        return write(file);
    }

    boolean saveAs() {
        if (book == null) return true;
        commit();
        String name = (book.info().title().isBlank() ? msg.get("outline.untitled") : book.info().title())
                .replaceAll("[/\\\\:*?\"<>|]", "-").strip();
        Path suggested = file != null ? file : suggestedFile != null ? suggestedFile : folder().resolve(name + ".epub");
        Optional<Path> target = ask.saveAs(suggested);
        if (target.isEmpty()) return false;
        Path p = target.get();
        if (!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".epub")) p = p.resolveSibling(p.getFileName() + ".epub");
        if (!write(p)) return false;
        file = p;
        prefs.addRecent(p);
        if (p.toAbsolutePath().getParent() != null) prefs.set("folder", p.toAbsolutePath().getParent());
        updateTitle();
        return true;
    }

    private boolean write(Path target) {
        commit();
        book.dropUnused();
        try {
            if (Files.exists(target)) backups.keep(target, LocalDateTime.ofInstant(clock.get(), ZoneId.systemDefault()));
            new EpubWriter(book, clock.get(), AmadeBookEditorApplication.version()).save(target);
            dirty = false;
            updateTitle();
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not save {}", target, e);
            ask.error(msg.format("error.save", target.getFileName(), String.valueOf(e.getMessage())));
            return false;
        }
    }

    /** Every two minutes: a book that has its file is saved, if that is switched on. */
    void autosave() {
        if (book == null || file == null || !autosaveItem.isSelected()) return;
        commit();
        if (dirty) write(file);
    }

    /** Asks about unsaved changes; false if the writer wants to go back to the book. */
    boolean closeCurrent() {
        if (book == null) return true;
        commit();
        if (!dirty) return true;
        String title = book.info().title().isBlank() ? msg.get("outline.untitled") : book.info().title();
        return switch (ask.unsaved(title)) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    void exit() {
        if (!closeCurrent()) return;
        prefs.set("preview", previewButton.isSelected());
        autosaveTimer.stop();
        liveTimer.stop();
        if (checkDialog != null) checkDialog.dispose();
        dispose();
        exitAction.run();
    }

    // ================================================================ view

    void setPreview(boolean on) {
        previewButton.setSelected(on);
        previewItem.setSelected(on);
        if (on) {
            split.setRightComponent(preview);
            split.setDividerLocation(Math.max(300, split.getWidth() - 380));
            updatePreview(true);
        } else {
            split.setRightComponent(null);
        }
        split.revalidate();
        prefs.set("preview", on);
    }

    private void about() {
        javax.swing.JOptionPane.showMessageDialog(this,
                "<html><center><h2>" + msg.get("window.title") + " " + AmadeBookEditorApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    /** For tests: the action behind a menu item. */
    Action actionFor(String key) {
        return actions.get(key);
    }
}
