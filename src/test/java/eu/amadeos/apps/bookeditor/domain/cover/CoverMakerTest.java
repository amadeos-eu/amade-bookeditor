package eu.amadeos.apps.bookeditor.domain.cover;

import eu.amadeos.apps.bookeditor.TestBooks;
import eu.amadeos.apps.bookeditor.domain.book.Resource;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverMakerTest {
    @Test
    void aCoverHasTheSizeShopsAskForAndIsAJpeg() throws Exception {
        CoverDesign d = new CoverDesign("Harbour Light", "A novel", "Anna Berg", CoverDesign.COLOURS[0], null,
                CoverDesign.Placement.TOP, CoverDesign.Typeface.SERIF);
        BufferedImage img = CoverMaker.draw(d, CoverMaker.WIDTH, CoverMaker.HEIGHT);
        assertEquals(1600, img.getWidth());
        assertEquals(2400, img.getHeight());
        byte[] jpeg = CoverMaker.jpeg(img);
        assertEquals((byte) 0xFF, jpeg[0]);
        assertEquals((byte) 0xD8, jpeg[1]);
        assertTrue(Pictures.read(jpeg).isPresent());
    }

    @Test
    void theTitleIsDrawnInAContrastingColour() {
        CoverDesign d = new CoverDesign("TITLE", "", "", Color.BLACK, null, CoverDesign.Placement.MIDDLE,
                CoverDesign.Typeface.SANS);
        BufferedImage img = CoverMaker.draw(d, 400, 600);
        boolean light = false;
        for (int y = 200; y < 400 && !light; y++) {
            for (int x = 0; x < 400; x++) {
                if ((img.getRGB(x, y) & 0xFF) > 200) {
                    light = true;
                    break;
                }
            }
        }
        assertTrue(light, "white title text on black");
        assertTrue(CoverMaker.dark(Color.BLACK));
    }

    @Test
    void longTitlesAreWrapped() {
        BufferedImage img = CoverMaker.draw(new CoverDesign("A very long title that cannot fit on one single line of a cover",
                "", "Author", CoverDesign.COLOURS[1], null, CoverDesign.Placement.BOTTOM, CoverDesign.Typeface.CONDENSED),
                400, 600);
        assertEquals(400, img.getWidth());
    }

    @Test
    void bigPicturesAreShrunkKeepingTheirFormat() {
        Resource big = new Resource("wide.png", "image/png", TestBooks.png(3200, 800, Color.BLUE));
        Optional<Resource> small = Pictures.shrink(big);
        assertTrue(small.isPresent());
        assertEquals("wide.png", small.get().name());
        BufferedImage img = Pictures.read(small.get().data()).orElseThrow();
        assertEquals(Pictures.MAX_SIDE, img.getWidth());
        assertEquals(400, img.getHeight());
    }

    @Test
    void picturesThatCannotBeReadAreNotShrunk() {
        assertTrue(Pictures.shrink(new Resource("x.png", "image/png", new byte[]{1, 2, 3})).isEmpty());
        assertTrue(Pictures.shrink(new Resource("x.gif", "image/gif", TestBooks.png(10, 10, Color.RED))).isEmpty());
        assertNotEquals(0, Pictures.MAX_SIDE);
    }
}
