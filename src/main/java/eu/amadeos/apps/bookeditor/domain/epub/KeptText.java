package eu.amadeos.apps.bookeditor.domain.epub;

import eu.amadeos.apps.bookeditor.domain.book.Block;
import eu.amadeos.apps.bookeditor.domain.book.ParaStyle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/** Turns kept content into plain paragraphs the writer can edit: a line per table row or list item. */
public final class KeptText {
    private KeptText() {
    }

    /** The words of kept content, with a space between cells and items that the markup alone runs together. */
    public static String words(Element content) {
        Element copy = content.clone();
        for (Element cell : copy.select("td, th, li, dt, dd, p, div, mi, mo, mn, mtext, text")) cell.appendText(" ");
        return copy.text().strip();
    }

    public static List<Block> paragraphs(Block.Kept kept) {
        Element root = Jsoup.parse(kept.xhtml(), "", Parser.xmlParser());
        List<Block> out = new ArrayList<>();
        List<Element> rows = root.select("tr");
        if (!rows.isEmpty()) {
            for (Element row : rows) {
                List<String> cells = new ArrayList<>();
                for (Element cell : row.children()) if (!cell.text().isBlank()) cells.add(cell.text().strip());
                if (!cells.isEmpty()) out.add(new Block.Paragraph(ParaStyle.TEXT, String.join(" – ", cells)));
            }
        } else if (!root.select("li, dt, dd").isEmpty()) {
            for (Element item : root.select("li, dt, dd")) {
                // Nested lists: only the item's own text, its sub-items follow as items of their own
                Element copy = item.clone();
                copy.select("ul, ol, dl").remove();
                String text = copy.text().strip();
                if (text.isEmpty()) continue;
                boolean numbered = item.parent() != null && item.parent().tagName().equals("ol");
                String mark = item.tagName().equals("li") ? numbered ? (item.elementSiblingIndex() + 1) + ". " : "• " : "";
                out.add(new Block.Paragraph(ParaStyle.TEXT, mark + text));
            }
        } else if (!kept.text().isBlank()) {
            out.add(new Block.Paragraph(ParaStyle.TEXT, kept.text()));
        }
        return out;
    }
}
