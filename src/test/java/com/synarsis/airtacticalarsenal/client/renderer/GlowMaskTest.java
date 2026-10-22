package com.synarsis.airtacticalarsenal.client.renderer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Glow masks of the lamps")
class GlowMaskTest {

    private static final Path DIR = Path.of("src", "main", "resources", "assets", "ata", "textures", "entity");

    private static BufferedImage read(String name) throws IOException {
        return ImageIO.read(Files.newInputStream(DIR.resolve(name)));
    }

    private static int lit(BufferedImage mask) {
        int n = 0;
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if ((mask.getRGB(x, y) >>> 24) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    @DisplayName("every vehicle has the masks it needs, as big as its atlas, with a few lit pixels only")
    void masks() throws IOException {
        String[][] masks = {
                {"iskander_tel", "front", "rear"},
                {"patriot_m983", "front", "rear"},
                {"patriot_pac2", "rear"},
                {"patriot_anmpq65", "rear"},
        };
        for (String[] entry : masks) {
            BufferedImage atlas = read(entry[0] + ".png");
            for (int i = 1; i < entry.length; i++) {
                BufferedImage mask = read(entry[0] + "_glow_" + entry[i] + ".png");
                assertEquals(atlas.getWidth(), mask.getWidth(), entry[0] + " " + entry[i]);
                assertEquals(atlas.getHeight(), mask.getHeight(), entry[0] + " " + entry[i]);
                int n = lit(mask);
                assertTrue(n >= 4 && n < 200, entry[0] + " " + entry[i] + " lit pixels " + n);
            }
        }
    }

    @Test
    @DisplayName("a lit pixel is the atlas pixel brightened by 1.3, fully opaque; the rest is clear")
    void pixels() throws IOException {
        for (String name : new String[] {"iskander_tel", "patriot_m983", "patriot_pac2", "patriot_anmpq65"}) {
            BufferedImage atlas = read(name + ".png");
            for (String group : new String[] {"front", "rear"}) {
                if (!Files.exists(DIR.resolve(name + "_glow_" + group + ".png"))) {
                    continue;
                }
                BufferedImage mask = read(name + "_glow_" + group + ".png");
                for (int y = 0; y < mask.getHeight(); y++) {
                    for (int x = 0; x < mask.getWidth(); x++) {
                        int m = mask.getRGB(x, y);
                        if ((m >>> 24) == 0) {
                            continue;
                        }
                        assertEquals(255, m >>> 24);
                        int a = atlas.getRGB(x, y);
                        for (int shift : new int[] {16, 8, 0}) {
                            int source = (a >> shift) & 255;
                            int expected = Math.min(255, (int) (source * 1.3 + 0.5));
                            assertEquals(expected, (m >> shift) & 255, 1.0, name + " " + group + " " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the front and the rear masks of a vehicle never light the same pixel")
    void groupsAreApart() throws IOException {
        for (String name : new String[] {"iskander_tel", "patriot_m983"}) {
            BufferedImage front = read(name + "_glow_front.png");
            BufferedImage rear = read(name + "_glow_rear.png");
            for (int y = 0; y < front.getHeight(); y++) {
                for (int x = 0; x < front.getWidth(); x++) {
                    assertTrue((front.getRGB(x, y) >>> 24) == 0 || (rear.getRGB(x, y) >>> 24) == 0,
                            name + " " + x + "," + y);
                }
            }
        }
    }
}
