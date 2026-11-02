package eu.amadeos.apps.bookeditor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** E-books as other programs make them, for the tests of taking such books over. */
public final class ForeignBooks {
    /** A small PNG picture. */
    public static final byte[] PNG = png();

    private static byte[] png() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(160, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x2E, 0x4A, 0x6B), 0, 100, new java.awt.Color(0xE8, 0xB0, 0x5A)));
            g.fillRect(0, 0, 160, 100);
            g.setColor(new java.awt.Color(0xFF, 0xE6, 0x99));
            g.fillOval(68, 30, 24, 30);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private ForeignBooks() {
    }

    /**
     * An EPUB 3 as a modern program makes it: navigation document, cover page, title page, dedication, chapters
     * with grouped headings, footnotes in asides, a table, a list, a formula, a picture and links.
     */
    public static Path epub3(Path file) throws IOException {
        Map<String, byte[]> f = new LinkedHashMap<>();
        f.put("META-INF/container.xml", container("OEBPS/package.opf"));
        f.put("OEBPS/package.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="en">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="uid">urn:uuid:11111111-2222-3333-4444-555555555555</dc:identifier>
                <dc:title id="t">The Lighthouse Keeper</dc:title>
                <dc:creator id="c1">Mara Lind</dc:creator>
                <meta refines="#c1" property="role" scheme="marc:relators">aut</meta>
                <dc:language>en</dc:language>
                <meta property="dcterms:modified">2025-01-01T00:00:00Z</meta>
                </metadata>
                <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="css" href="css/style.css" media-type="text/css"/>
                <item id="coverimg" href="img/cover.png" media-type="image/png" properties="cover-image"/>
                <item id="lamp" href="img/lamp.png" media-type="image/png"/>
                <item id="cover" href="text/cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="title" href="text/title.xhtml" media-type="application/xhtml+xml"/>
                <item id="ded" href="text/dedication.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/one.xhtml" media-type="application/xhtml+xml" properties="mathml"/>
                <item id="c2" href="text/two.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                <itemref idref="cover"/><itemref idref="title"/><itemref idref="ded"/><itemref idref="nav"/>
                <itemref idref="c1"/><itemref idref="c2"/>
                </spine>
                </package>
                """.getBytes(StandardCharsets.UTF_8));
        f.put("OEBPS/nav.xhtml", page("Contents", """
                <nav epub:type="toc"><h1>Contents</h1><ol>
                <li><a href="text/one.xhtml">One</a></li><li><a href="text/two.xhtml">Two</a></li></ol></nav>
                """));
        f.put("OEBPS/css/style.css", """
                .strong { font-weight: bold }
                p.middle { text-align: center }
                """.getBytes(StandardCharsets.UTF_8));
        f.put("OEBPS/img/cover.png", PNG);
        f.put("OEBPS/img/lamp.png", PNG);
        f.put("OEBPS/text/cover.xhtml", page("Cover", "<section epub:type=\"cover\"><img src=\"../img/cover.png\" alt=\"\"/></section>"));
        f.put("OEBPS/text/title.xhtml", page("Title", "<section epub:type=\"titlepage\"><h1>The Lighthouse Keeper</h1><p>Mara Lind</p></section>"));
        f.put("OEBPS/text/dedication.xhtml", page("Dedication", "<section epub:type=\"dedication\"><p>For Jonas.</p></section>"));
        f.put("OEBPS/text/one.xhtml", page("One", """
                <section epub:type="chapter">
                <hgroup><h2>I</h2><p>The Storm</p></hgroup>
                <p>It was a <em>dark</em> and <span class="strong">stormy</span> night.<a href="#n1" epub:type="noteref">1</a></p>
                <p class="middle">* * *</p>
                <p class="middle">The End of the Beginning</p>
                <blockquote><p>The sea does not forgive.</p></blockquote>
                <figure><img src="../img/lamp.png" alt="An oil lamp"/><figcaption>The old lamp</figcaption></figure>
                <table><tr><th>Day</th><th>Weather</th></tr><tr><td>Monday</td><td>Rain</td></tr></table>
                <ul><li>Rope</li><li>Lantern</li></ul>
                <math xmlns="http://www.w3.org/1998/Math/MathML"><mi>x</mi><mo>=</mo><mn>2</mn></math>
                <p>Read on in <a href="two.xhtml">the next chapter</a> or at <a href="https://example.org/">the museum</a>.</p>
                <video src="film.mp4"></video>
                </section>
                <aside id="n1" epub:type="footnote"><p><a href="#r1">1.</a> The stairs had 211 steps.</p></aside>
                """));
        f.put("OEBPS/text/two.xhtml", page("Two", """
                <section epub:type="chapter">
                <hgroup><h2>II</h2><p>The Morning</p></hgroup>
                <p>The sun came up.</p>
                <h3>On the Rocks</h3>
                <p>Gulls.</p>
                </section>
                """));
        return zip(file, f);
    }

    /**
     * An EPUB 2 as older converters make it: an NCX, a guide, the cover named in a meta, bold set through a class,
     * a book title in {@code h1} with chapters in {@code h2}, a chapter split over two files and endnotes in a
     * file of their own.
     */
    public static Path epub2(Path file) throws IOException {
        Map<String, byte[]> f = new LinkedHashMap<>();
        f.put("META-INF/container.xml", container("content.opf"));
        f.put("content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:title>Old Harbour</dc:title>
                <dc:creator opf:role="aut">Jon Sand</dc:creator>
                <dc:creator opf:role="ill">Ida Sand</dc:creator>
                <dc:language>de</dc:language>
                <dc:identifier id="id">isbn-000</dc:identifier>
                <meta name="cover" content="cov"/>
                </metadata>
                <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="css" href="stylesheet.css" media-type="text/css"/>
                <item id="cov" href="images/cover.png" media-type="image/png"/>
                <item id="coverpage" href="titlepage.xhtml" media-type="application/xhtml+xml"/>
                <item id="a" href="text/part_split_000.html" media-type="application/xhtml+xml"/>
                <item id="b" href="text/part_split_001.html" media-type="application/xhtml+xml"/>
                <item id="c" href="text/chapter2.html" media-type="application/xhtml+xml"/>
                <item id="n" href="text/notes.html" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                <itemref idref="coverpage"/><itemref idref="a"/><itemref idref="b"/><itemref idref="c"/><itemref idref="n"/>
                </spine>
                <guide><reference type="cover" href="titlepage.xhtml" title="Cover"/></guide>
                </package>
                """.getBytes(StandardCharsets.UTF_8));
        f.put("toc.ncx", "<ncx/>".getBytes(StandardCharsets.UTF_8));
        f.put("stylesheet.css", """
                /* made by a converter */
                .calibre1 { font-weight: bold; }
                .calibre2, .calibre3 { font-style: italic }
                """.getBytes(StandardCharsets.UTF_8));
        f.put("images/cover.png", PNG);
        f.put("titlepage.xhtml", page("Cover", "<div><img src=\"images/cover.png\" alt=\"cover.png\"/></div>"));
        f.put("text/part_split_000.html", page("Part", """
                <h1 class="title">Old Harbour</h1>
                <div class="chapter">
                <h2>Erstes Kapitel</h2>
                <p class="text">Er kam <span class="calibre1">spät</span> und <span class="calibre3">müde</span>.<sup><a href="notes.html#fn1" id="r1">1</a></sup></p>
                </div>
                """));
        f.put("text/part_split_001.html", page("Part", """
                <p class="text">Noch am selben Abend ging er zum Hafen. Siehe <a href="chapter2.html#x">später</a>.</p>
                """));
        f.put("text/chapter2.html", page("Chapter 2", """
                <h2 id="x">Zweites Kapitel</h2>
                <p class="text">Der Morgen kam.<sup><a href="notes.html#fn2" id="r2">2</a></sup></p>
                <p class="text">***</p>
                <p class="text">Ende.</p>
                """));
        f.put("text/notes.html", page("Notes", """
                <h2>Anmerkungen</h2>
                <p id="fn1"><a href="part_split_000.html#r1">1</a> Es war fast Mitternacht.</p>
                <p><a id="fn2" href="chapter2.html#r2">2</a>. Über dem Wasser.</p>
                """));
        return zip(file, f);
    }

    private static byte[] container(String opf) {
        return ("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"" + opf + "\" media-type=\"application/oebps-package+xml\"/>"
                + "</rootfiles></container>").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] page(String title, String body) {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>%s</title><link rel="stylesheet" href="../css/style.css"/></head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, body)).getBytes(StandardCharsets.UTF_8);
    }

    private static Path zip(Path file, Map<String, byte[]> files) throws IOException {
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(os)) {
            byte[] mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry m = new ZipEntry("mimetype");
            m.setMethod(ZipEntry.STORED);
            m.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            m.setCrc(crc.getValue());
            zip.putNextEntry(m);
            zip.write(mimetype);
            zip.closeEntry();
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }
}
