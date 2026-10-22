package com.synarsis.airtacticalarsenal.lighting;

import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("A light section is copied from the engine arrays")
class LightLevelCopyTest {

    private static int at(byte[] out, int x, int y, int z) {
        return out[(y * 16 + z) * 16 + x] & 255;
    }

    @Test
    @DisplayName("with both arrays every block gets its own sky and block level, edges included, with no lookups")
    void bothArrays() {
        DataLayer sky = new DataLayer();
        DataLayer block = new DataLayer();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    sky.set(x, y, z, (x + y) & 15);
                    block.set(x, y, z, (z * 3 + y) & 15);
                }
            }
        }
        AtomicInteger calls = new AtomicInteger();
        byte[] out = new byte[LightLevelCopy.SECTION_BLOCKS];
        LightLevelCopy.copy(sky, block, true, (x, z) -> {
            calls.incrementAndGet();
            return 0;
        }, out);
        assertEquals(0, calls.get());
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals(LightLevelCopy.pack((x + y) & 15, (z * 3 + y) & 15), at(out, x, y, z));
                }
            }
        }
        assertEquals(LightLevelCopy.pack(14, (45 + 15) & 15), at(out, 15, 15, 15));
        assertEquals(LightLevelCopy.pack(0, 0), at(out, 0, 0, 0));
    }

    @Test
    @DisplayName("without a sky array the sky level is read once per column and is the same over the whole height")
    void noSkyArray() {
        DataLayer block = new DataLayer();
        block.set(3, 9, 5, 12);
        AtomicInteger calls = new AtomicInteger();
        byte[] out = new byte[LightLevelCopy.SECTION_BLOCKS];
        LightLevelCopy.copy(null, block, true, (x, z) -> {
            calls.incrementAndGet();
            return (x * 16 + z) % 16;
        }, out);
        assertEquals(256, calls.get());
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z += 5) {
                for (int x = 0; x < 16; x += 3) {
                    int expectedBlock = x == 3 && y == 9 && z == 5 ? 12 : 0;
                    assertEquals(LightLevelCopy.pack((x * 16 + z) % 16, expectedBlock), at(out, x, y, z));
                }
            }
        }
        assertEquals(LightLevelCopy.pack(5, 12), at(out, 3, 9, 5));
    }

    @Test
    @DisplayName("without a block array the block light is 0, and a dimension without sky has 0 sky light and no lookups")
    void noBlockArrayAndNoSky() {
        DataLayer sky = new DataLayer(15);
        byte[] out = new byte[LightLevelCopy.SECTION_BLOCKS];
        java.util.Arrays.fill(out, (byte) 0x77);
        LightLevelCopy.copy(sky, null, true, (x, z) -> {
            throw new AssertionError("no column lookup is needed");
        }, out);
        for (int i = 0; i < out.length; i++) {
            assertEquals(LightLevelCopy.pack(15, 0), out[i] & 255);
        }
        AtomicInteger calls = new AtomicInteger();
        java.util.Arrays.fill(out, (byte) 0x77);
        LightLevelCopy.copy(null, null, false, (x, z) -> {
            calls.incrementAndGet();
            return 9;
        }, out);
        assertEquals(0, calls.get());
        for (int i = 0; i < out.length; i++) {
            assertEquals(0, out[i]);
        }
    }

    @Test
    @DisplayName("a section reads at most 256 lookups, where the old walk made 8192")
    void cost() {
        int old = 2 * LightLevelCopy.SECTION_BLOCKS;
        AtomicInteger calls = new AtomicInteger();
        byte[] out = new byte[LightLevelCopy.SECTION_BLOCKS];
        LightLevelCopy.copy(null, null, true, (x, z) -> {
            calls.incrementAndGet();
            return 15;
        }, out);
        assertEquals(256, calls.get());
        assertEquals(8192, old);
    }
}
