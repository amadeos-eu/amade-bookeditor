package eu.amadeos.apps.bookeditor.ui.icons;

import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The toolbar glyphs only the Book Editor needs, drawn in the style of WritePad's; new, open, save, undo
 * and redo come from {@link StandardGlyph}.
 */
public enum BookGlyph implements ToolIcon.Glyph {
    BOLD, ITALIC, PICTURE, FOOTNOTE, LINK, SCENE_BREAK, PREVIEW, CHECK;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case BOLD -> pen.letter("B", new Font(Font.SERIF, Font.BOLD, 14), 13.5);
            case ITALIC -> pen.letter("I", new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 14), 13.5);
            case PICTURE -> picture(pen);
            case FOOTNOTE -> footnote(pen);
            case LINK -> link(pen);
            case SCENE_BREAK -> sceneBreak(pen);
            case PREVIEW -> reader(pen);
            case CHECK -> check(pen);
        }
    }

    /** A framed landscape: a hill and the sun (the same picture as WritePad's). */
    private static void picture(ToolIcon.Pen pen) {
        pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
        Path2D hill = new Path2D.Double();
        hill.moveTo(2, 13);
        hill.lineTo(6, 7.5);
        hill.lineTo(9, 10.5);
        hill.lineTo(11, 8.5);
        hill.lineTo(14, 13);
        hill.closePath();
        pen.fill(hill, pen.ink());
        pen.fill(new Ellipse2D.Double(10, 4, 3, 3), pen.light());
    }

    /** A page with a raised note number in the text and the note under a short rule at the bottom. */
    private static void footnote(ToolIcon.Pen pen) {
        pen.page(2.5, 1.5, 11, 13);
        textLine(pen, 5, 4, 6);
        textLine(pen, 8.5, 4, 4.5);
        // The raised "1", drawn as strokes so it stays sharp at this size.
        pen.bold(1.3f);
        pen.line(10.8, 5.6, 10.8, 9.2);
        pen.line(9.6, 6.6, 10.8, 5.6);
        pen.thin();
        pen.line(4, 10.5, 7.5, 10.5);
        textLine(pen, 12, 4, 7.5);
    }

    /** Two chain links, tilted. */
    private static void link(ToolIcon.Pen pen) {
        AffineTransform saved = pen.g().getTransform();
        pen.g().rotate(-Math.PI / 4, 8, 8);
        pen.bold(1.7f);
        pen.draw(new RoundRectangle2D.Double(0.8, 5.6, 8, 4.8, 4.8, 4.8));
        pen.draw(new RoundRectangle2D.Double(7.2, 5.6, 8, 4.8, 4.8, 4.8));
        pen.thin();
        pen.g().setTransform(saved);
    }

    /** Text above and below, three asterisks in the middle: the classic "* * *". */
    private static void sceneBreak(ToolIcon.Pen pen) {
        textLine(pen, 2, 1.5, 13);
        for (double x : new double[]{3.5, 8, 12.5}) star(pen, x, 8, 2.2);
        textLine(pen, 14, 1.5, 9);
    }

    /** An e-reader: a device with a page of text on its screen. */
    private static void reader(ToolIcon.Pen pen) {
        pen.fillAndDraw(new RoundRectangle2D.Double(2.5, 0.5, 11, 15, 3, 3), pen.paper());
        pen.draw(new Rectangle2D.Double(4.5, 2.5, 7, 9));
        for (int i = 0; i < 4; i++) textLine(pen, 3.8 + i * 2, 5.5, i == 3 ? 3 : 5);
        pen.fill(new Rectangle2D.Double(6.5, 13, 3, 1), pen.ink());
    }

    /** A page with a big tick: the book is checked. */
    private static void check(ToolIcon.Pen pen) {
        pen.page(1.5, 1.5, 10, 13);
        textLine(pen, 4.5, 3.5, 5);
        textLine(pen, 7, 3.5, 4);
        Path2D tick = new Path2D.Double();
        tick.moveTo(6.5, 10);
        tick.lineTo(9, 12.8);
        tick.lineTo(14.5, 5.5);
        pen.g().setColor(pen.light());
        pen.bold(2.4f);
        pen.g().draw(tick);
        pen.thin();
    }

    /** One line of text as a thin bar. */
    private static void textLine(ToolIcon.Pen pen, double y, double x, double w) {
        pen.fill(new Rectangle2D.Double(x, y - 0.6, w, 1.2), pen.ink());
    }

    /** An asterisk of three crossing strokes. */
    private static void star(ToolIcon.Pen pen, double cx, double cy, double r) {
        pen.bold(1.2f);
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 2 + i * Math.PI / 3;
            double dx = r * Math.cos(a);
            double dy = r * Math.sin(a);
            pen.line(cx - dx, cy - dy, cx + dx, cy + dy);
        }
        pen.thin();
    }
}
