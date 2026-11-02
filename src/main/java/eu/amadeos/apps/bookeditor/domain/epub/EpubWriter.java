package eu.amadeos.apps.bookeditor.domain.epub;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.Book;
import eu.amadeos.apps.bookeditor.domain.book.BookInfo;
import eu.amadeos.apps.bookeditor.domain.book.Chapter;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import eu.amadeos.apps.bookeditor.domain.book.Person;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import eu.amadeos.apps.bookeditor.domain.book.Role;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a book as an EPUB 3 file: the package with metadata and accessibility details, the navigation (with an
 * EPUB 2 table of contents for older readers), cover and title page, the chapters, the style sheet of the look and
 * the pictures.
 */
public final class EpubWriter {
    /** The prefix of the editor's own metadata; a book carrying it can be opened again in the editor. */
    public static final String PREFIX = "amadeos: https://amadeos.eu/ns/book-editor#";
    public static final String EDITOR_PROPERTY = "amadeos:editor";
    public static final String LOOK_PROPERTY = "amadeos:look";
    public static final String ISBN_PROPERTY = "amadeos:isbn";
    static final String OPF = "EPUB/package.opf";

    private final Book book;
    private final Instant modified;
    private final String editorVersion;

    public EpubWriter(Book book, Instant modified, String editorVersion) {
        this.book = book;
        this.modified = modified.truncatedTo(ChronoUnit.SECONDS);
        this.editorVersion = editorVersion;
    }

    /**
     * Writes the book to {@code file} through a temporary file next to it, so a failure (a full disk) leaves the
     * old file as it was.
     */
    public void save(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, ".book-editor-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                write(out);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // --8<-- [start:zip]
    public void write(OutputStream target) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8);
        // The first entry must be "mimetype", stored without compression and without extra fields.
        byte[] mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(mimetype.length);
        CRC32 crc = new CRC32();
        crc.update(mimetype);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(mimetype);
        zip.closeEntry();

        put(zip, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
                </rootfiles>
                </container>
                """.formatted(OPF));
        put(zip, OPF, opf());
        put(zip, "EPUB/nav.xhtml", nav());
        put(zip, "EPUB/toc.ncx", ncx());
        put(zip, "EPUB/styles/book.css", book.look().css());
        book.cover().ifPresent(c -> putUnchecked(zip, "EPUB/text/cover.xhtml", coverPage(c)));
        put(zip, "EPUB/text/titlepage.xhtml", titlePage());
        for (Chapter chapter : textChapters()) {
            put(zip, "EPUB/text/" + ChapterWriter.fileName(chapter), ChapterWriter.write(book, chapter));
        }
        for (Resource r : book.resources().values()) {
            zip.putNextEntry(new ZipEntry("EPUB/images/" + r.name()));
            zip.write(r.data());
            zip.closeEntry();
        }
        zip.finish();
    }
    // --8<-- [end:zip]

    /** The dedication (if any) and the chapters, in reading order. */
    private List<Chapter> textChapters() {
        List<Chapter> list = new ArrayList<>();
        book.dedication().ifPresent(list::add);
        list.addAll(book.chapters());
        return list;
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putUnchecked(ZipOutputStream zip, String name, String text) {
        try {
            put(zip, name, text);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String imageId(String name) {
        return "img-" + name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    // ---------------------------------------------------------------- package document

    // --8<-- [start:metadata]
    String opf() {
        BookInfo info = book.info();
        BookTexts texts = BookTexts.of(info.language());
        StringBuilder m = new StringBuilder();
        m.append("<dc:identifier id=\"book-id\">").append(Xml.text(info.identifier())).append("</dc:identifier>\n");
        String isbn = info.isbn().replaceAll("[^0-9Xx]", "");
        if (!isbn.isEmpty()) {
            m.append("<dc:identifier id=\"isbn\">urn:isbn:").append(isbn).append("</dc:identifier>\n");
            // The ISBN as typed, with its hyphens, for the Book Details dialog
            m.append("<meta property=\"").append(ISBN_PROPERTY).append("\">").append(Xml.text(info.isbn())).append("</meta>\n");
        }
        String title = info.title().isEmpty() ? texts.titlePage() : info.title();
        m.append("<dc:title id=\"title\">").append(Xml.text(title)).append("</dc:title>\n");
        m.append("<meta refines=\"#title\" property=\"title-type\">main</meta>\n");
        if (!info.subtitle().isEmpty()) {
            m.append("<dc:title id=\"subtitle\">").append(Xml.text(info.subtitle())).append("</dc:title>\n");
            m.append("<meta refines=\"#subtitle\" property=\"title-type\">subtitle</meta>\n");
        }
        int n = 0;
        for (Person p : info.people()) {
            String id = "person-" + (++n);
            String element = p.role() == Role.AUTHOR ? "dc:creator" : "dc:contributor";
            m.append('<').append(element).append(" id=\"").append(id).append("\">").append(Xml.text(p.name()))
                    .append("</").append(element).append(">\n");
            m.append("<meta refines=\"#").append(id).append("\" property=\"role\" scheme=\"marc:relators\">")
                    .append(p.role().code()).append("</meta>\n");
        }
        m.append("<dc:language>").append(Xml.text(info.language())).append("</dc:language>\n");
        if (!info.publisher().isEmpty()) m.append("<dc:publisher>").append(Xml.text(info.publisher())).append("</dc:publisher>\n");
        if (info.published().matches("\\d{4}(-\\d{2}(-\\d{2})?)?")) {
            m.append("<dc:date>").append(info.published()).append("</dc:date>\n");
        }
        if (!info.description().isEmpty()) m.append("<dc:description>").append(Xml.text(info.description())).append("</dc:description>\n");
        for (String k : info.keywords()) m.append("<dc:subject>").append(Xml.text(k)).append("</dc:subject>\n");
        m.append("<dc:rights>").append(Xml.text(rights())).append("</dc:rights>\n");
        if (!info.series().isEmpty()) {
            m.append("<meta property=\"belongs-to-collection\" id=\"series\">").append(Xml.text(info.series())).append("</meta>\n");
            m.append("<meta refines=\"#series\" property=\"collection-type\">series</meta>\n");
            if (info.volume() > 0) {
                m.append("<meta refines=\"#series\" property=\"group-position\">").append(info.volume()).append("</meta>\n");
            }
        }
        m.append("<meta property=\"dcterms:modified\">").append(modified).append("</meta>\n");
        accessibility(m);
        m.append("<meta property=\"").append(EDITOR_PROPERTY).append("\">amadeOS Book Editor ")
                .append(Xml.text(editorVersion)).append("</meta>\n");
        m.append("<meta property=\"").append(LOOK_PROPERTY).append("\">").append(book.look().key()).append("</meta>\n");
        book.cover().ifPresent(c -> m.append("<meta name=\"cover\" content=\"").append(imageId(c)).append("\"/>\n"));

        StringBuilder manifest = new StringBuilder();
        manifest.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
        manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n");
        manifest.append("<item id=\"css\" href=\"styles/book.css\" media-type=\"text/css\"/>\n");
        StringBuilder spine = new StringBuilder();
        if (book.cover().isPresent()) {
            manifest.append("<item id=\"cover\" href=\"text/cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
            spine.append("<itemref idref=\"cover\"/>\n");
        }
        manifest.append("<item id=\"titlepage\" href=\"text/titlepage.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
        spine.append("<itemref idref=\"titlepage\"/>\n");
        book.dedication().ifPresent(d -> {
            manifest.append("<item id=\"dedication\" href=\"text/dedication.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
            spine.append("<itemref idref=\"dedication\"/>\n");
        });
        spine.append("<itemref idref=\"nav\"/>\n");
        for (Chapter c : book.chapters()) {
            String id = "chapter-" + c.id();
            String properties = ChapterWriter.properties(c);
            manifest.append("<item id=\"").append(id).append("\" href=\"text/").append(ChapterWriter.fileName(c))
                    .append("\" media-type=\"application/xhtml+xml\"")
                    .append(properties.isEmpty() ? "" : " properties=\"" + properties + "\"").append("/>\n");
            spine.append("<itemref idref=\"").append(id).append("\"/>\n");
        }
        for (Resource r : book.resources().values()) {
            manifest.append("<item id=\"").append(imageId(r.name())).append("\" href=\"images/").append(Xml.attr(r.name()))
                    .append("\" media-type=\"").append(r.mediaType()).append('"');
            if (book.cover().map(r.name()::equals).orElse(false)) manifest.append(" properties=\"cover-image\"");
            manifest.append("/>\n");
        }
        String lang = Xml.attr(info.language());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" \
                xml:lang="%s" prefix="%s">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                %s</metadata>
                <manifest>
                %s</manifest>
                <spine toc="ncx">
                %s</spine>
                </package>
                """.formatted(lang, PREFIX, m, manifest, spine);
    }
    // --8<-- [end:metadata]

    /** "© 2026 Anna Berger. CC BY 4.0 …", the year from the publication date or else from the last save. */
    private String rights() {
        BookInfo info = book.info();
        String year = info.published().matches("\\d{4}.*") ? info.published().substring(0, 4)
                : String.valueOf(modified.atZone(java.time.ZoneOffset.UTC).getYear());
        return info.license().rights(year, info.authorLine());
    }

    /**
     * The schema.org accessibility details (EPUB Accessibility 1.1): what the book is made of, what helps people
     * with disabilities, and a summary in words. Text alone is enough to read it when every picture is described.
     */
    private void accessibility(StringBuilder m) {
        List<Block.Picture> pictures = book.pictures().stream().map(java.util.Map.Entry::getValue).toList();
        boolean visual = !pictures.isEmpty() || book.cover().isPresent();
        boolean described = pictures.stream().allMatch(p -> !p.description().isEmpty());
        m.append("<meta property=\"schema:accessMode\">textual</meta>\n");
        if (visual) m.append("<meta property=\"schema:accessMode\">visual</meta>\n");
        if (!visual || described) m.append("<meta property=\"schema:accessModeSufficient\">textual</meta>\n");
        if (visual) m.append("<meta property=\"schema:accessModeSufficient\">textual,visual</meta>\n");
        List<String> features = new ArrayList<>(List.of("structuralNavigation", "tableOfContents", "readingOrder",
                "displayTransformability"));
        if (!pictures.isEmpty() && described) features.add("alternativeText");
        for (String f : features) m.append("<meta property=\"schema:accessibilityFeature\">").append(f).append("</meta>\n");
        m.append("<meta property=\"schema:accessibilityHazard\">none</meta>\n");
        String summary = book.info().summary().isEmpty() ? summary(pictures.size(), described) : book.info().summary();
        m.append("<meta property=\"schema:accessibilitySummary\">").append(Xml.text(summary)).append("</meta>\n");
    }

    private String summary(int pictures, boolean described) {
        String s = "A book of reflowable text with a table of contents and headings for each chapter.";
        if (pictures > 0) {
            s += described ? " All pictures have a text description." : " Some pictures have no text description.";
        }
        return s + " No flashing content, no sound.";
    }

    // ---------------------------------------------------------------- navigation

    private String headPart(String title, String css) {
        String lang = Xml.attr(book.info().language());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" \
                xml:lang="%s" lang="%s">
                <head>
                <title>%s</title>
                <link rel="stylesheet" type="text/css" href="%s"/>
                </head>
                """.formatted(lang, lang, Xml.text(title), css);
    }

    /** The table entry of a chapter: its title, or "Chapter 3" for one without. */
    String tocLabel(Chapter c) {
        if (!c.title().isEmpty()) return c.title();
        int number = book.numberOf(c.id());
        BookTexts texts = BookTexts.of(book.info().language());
        return number > 0 ? texts.chapter(number) : book.info().title();
    }

    /** The {@code h2} headings of a chapter, for the second level of the table of contents. */
    private static List<String> headings(Chapter c) {
        List<String> list = new ArrayList<>();
        for (Block b : c.blocks()) {
            if (b instanceof Block.Paragraph p && p.style() == ParaStyle.HEADING && !p.isEmpty()) list.add(p.plainText().strip());
        }
        return list;
    }

    String nav() {
        BookTexts texts = BookTexts.of(book.info().language());
        StringBuilder sb = new StringBuilder(headPart(texts.contents(), "styles/book.css"));
        sb.append("<body epub:type=\"frontmatter\">\n<nav epub:type=\"toc\" role=\"doc-toc\" id=\"toc\">\n<h1>")
                .append(Xml.text(texts.contents())).append("</h1>\n<ol>\n");
        for (Chapter c : book.chapters()) {
            String href = "text/" + ChapterWriter.fileName(c);
            sb.append("<li><a href=\"").append(href).append("\">").append(Xml.text(tocLabel(c))).append("</a>");
            List<String> heads = headings(c);
            if (!heads.isEmpty()) {
                sb.append("\n<ol>\n");
                for (int i = 0; i < heads.size(); i++) {
                    sb.append("<li><a href=\"").append(href).append('#').append(ChapterWriter.headingId(i + 1)).append("\">")
                            .append(Xml.text(heads.get(i))).append("</a></li>\n");
                }
                sb.append("</ol>\n");
            }
            sb.append("</li>\n");
        }
        sb.append("</ol>\n</nav>\n");
        sb.append("<nav epub:type=\"landmarks\" id=\"landmarks\" hidden=\"hidden\">\n<ol>\n");
        if (book.cover().isPresent()) {
            sb.append("<li><a epub:type=\"cover\" href=\"text/cover.xhtml\">").append(Xml.text(texts.cover())).append("</a></li>\n");
        }
        sb.append("<li><a epub:type=\"titlepage\" href=\"text/titlepage.xhtml\">").append(Xml.text(texts.titlePage())).append("</a></li>\n");
        sb.append("<li><a epub:type=\"toc\" href=\"#toc\">").append(Xml.text(texts.contents())).append("</a></li>\n");
        if (!book.chapters().isEmpty()) {
            sb.append("<li><a epub:type=\"bodymatter\" href=\"text/").append(ChapterWriter.fileName(book.chapters().getFirst()))
                    .append("\">").append(Xml.text(texts.start())).append("</a></li>\n");
        }
        sb.append("</ol>\n</nav>\n</body>\n</html>\n");
        return sb.toString();
    }

    String ncx() {
        StringBuilder points = new StringBuilder();
        int order = 0;
        for (Chapter c : book.chapters()) {
            order++;
            points.append("<navPoint id=\"np-").append(order).append("\" playOrder=\"").append(order).append("\">")
                    .append("<navLabel><text>").append(Xml.text(tocLabel(c))).append("</text></navLabel>")
                    .append("<content src=\"text/").append(ChapterWriter.fileName(c)).append("\"/></navPoint>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head>
                <meta name="dtb:uid" content="%s"/>
                <meta name="dtb:depth" content="1"/>
                <meta name="dtb:totalPageCount" content="0"/>
                <meta name="dtb:maxPageNumber" content="0"/>
                </head>
                <docTitle><text>%s</text></docTitle>
                <navMap>
                %s</navMap>
                </ncx>
                """.formatted(Xml.attr(book.info().identifier()), Xml.text(book.info().title()), points);
    }

    // ---------------------------------------------------------------- cover and title page

    private String coverPage(String cover) {
        BookTexts texts = BookTexts.of(book.info().language());
        return headPart(texts.cover(), "../styles/book.css") + """
                <body class="cover" epub:type="cover">
                <section epub:type="cover">
                <img src="../images/%s" alt="%s"/>
                </section>
                </body>
                </html>
                """.formatted(Xml.attr(cover), Xml.attr(book.info().title() + (book.info().authorLine().isEmpty() ? ""
                : " – " + book.info().authorLine())));
    }

    String titlePage() {
        BookInfo info = book.info();
        BookTexts texts = BookTexts.of(info.language());
        StringBuilder sb = new StringBuilder(headPart(info.title().isEmpty() ? texts.titlePage() : info.title(), "../styles/book.css"));
        sb.append("<body epub:type=\"frontmatter\">\n<section class=\"titlepage\" epub:type=\"titlepage\">\n");
        sb.append("<h1 class=\"book-title\">").append(Xml.text(info.title())).append("</h1>\n");
        if (!info.subtitle().isEmpty()) sb.append("<p class=\"book-subtitle\">").append(Xml.text(info.subtitle())).append("</p>\n");
        for (Person p : info.authors()) sb.append("<p class=\"book-author\">").append(Xml.text(p.name())).append("</p>\n");
        for (Person p : info.people()) {
            if (p.role() != Role.AUTHOR) sb.append("<p class=\"book-contributor\">").append(Xml.text(p.name())).append("</p>\n");
        }
        if (!info.series().isEmpty()) {
            sb.append("<p class=\"book-contributor\">").append(Xml.text(info.series()))
                    .append(info.volume() > 0 ? " " + info.volume() : "").append("</p>\n");
        }
        if (!info.publisher().isEmpty()) sb.append("<p class=\"book-publisher\">").append(Xml.text(info.publisher())).append("</p>\n");
        sb.append("<p class=\"book-rights\">").append(Xml.text(rights())).append("</p>\n");
        if (!info.isbn().isEmpty()) sb.append("<p class=\"book-rights\">ISBN ").append(Xml.text(info.isbn())).append("</p>\n");
        sb.append("</section>\n</body>\n</html>\n");
        return sb.toString();
    }
}
