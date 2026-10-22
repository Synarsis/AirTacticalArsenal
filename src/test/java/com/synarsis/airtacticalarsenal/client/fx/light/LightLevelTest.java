package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class LightLevelTest {
    @Test
    void packsEverySkyAndBlockLevel() {
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                int packed = LightLevelVolume.pack(sky, block);
                assertEquals(sky, packed >> 4);
                assertEquals(block, packed & 15);
            }
        }
    }

    @Test
    void dirtyNeighboursAndDelayedRetry() {
        LightLevelUpdates updates = new LightLevelUpdates();
        updates.changed(-2, -4, 3, 90);
        var first = updates.take(90, 24);
        assertEquals(7, new HashSet<>(first).size());
        assertTrue(first.contains(OccupancyGrid.sectionKey(-2, -4, 3)));
        assertTrue(first.contains(OccupancyGrid.sectionKey(-3, -4, 3)));
        assertTrue(updates.take(99, 24).isEmpty());
        assertEquals(new HashSet<>(first), new HashSet<>(updates.take(100, 24)));
        assertTrue(updates.take(101, 24).isEmpty());
    }

    @Test
    void fourRoundSectionsAndBudget() {
        LightLevelUpdates updates = new LightLevelUpdates();
        assertEquals(4, LightLevelUpdates.ROUND_PER_FRAME);
        HashSet<Integer> slots = new HashSet<>();
        for (int index = 0; index < OccupancyGrid.SECTION_COUNT; index++) {
            slots.add(updates.nextRound());
        }
        assertEquals(OccupancyGrid.SECTION_COUNT, slots.size());
        assertEquals(0, updates.nextRound());
        updates.changed(0, 0, 0, 0);
        assertEquals(3, updates.take(0, 3).size());
        assertEquals(4, updates.take(0, 24).size());
    }

    @Test
    void packedLevelsShareRingAndInvalidateOldSlots() {
        OccupancyGrid solid = new OccupancyGrid();
        OccupancyGrid light = new OccupancyGrid();
        solid.recenter(-33, 70, 48);
        light.recenter(-33, 70, 48);
        byte[] section = new byte[4096];
        java.util.Arrays.fill(section, (byte) LightLevelVolume.pack(15, 4));
        light.writeSection(-3, 4, 3, section);
        assertEquals(244, light.opacity(-33, 70, 48));
        assertEquals(solid.recenter(160, 70, 48), light.recenter(160, 70, 48));
        assertEquals(0, light.opacity(-33, 70, 48));
    }

    @Test
    void reconstructsGreyOnEveryFace() {
        for (double face : new double[] {0.5, 0.6, 0.8, 1.0}) {
            assertEquals(0.4, LightMath.recoverAlbedo(0.4 * 0.2 * face, 0.2, face, 0.1), 0.02);
        }
    }

    @Test
    void darknessBlendIsContinuous() {
        assertEquals(0.15, LightMath.recoverAlbedo(0, 0, 1, 0.15), 1.0e-12);
        double before = LightMath.recoverAlbedo(0.012, 0.03 - 1.0e-8, 1, 0.15);
        double after = LightMath.recoverAlbedo(0.012, 0.03 + 1.0e-8, 1, 0.15);
        assertEquals(before, after, 1.0e-6);
    }

    @Test
    void nearWallOverexposesAndZeroLightIsIdentity() {
        int[][] nightLightmap = {{14, 14, 14}, {44, 35, 27}, {35, 35, 35}, {124, 100, 76}};
        for (int[] texel : nightLightmap) {
            double[] brightness = java.util.Arrays.stream(texel).mapToDouble(value -> LightMath.toLinear(value / 255.0)).toArray();
            double[] scene = java.util.Arrays.stream(brightness).map(value -> LightMath.toSrgb(0.4 * value)).toArray();
            double[] light = {232, 232 * 0.86, 232 * 0.68};
            double[] lit = LightMath.compositeLit(scene, light, 1, brightness, 1);
            for (int channel = 0; channel < 3; channel++) {
                assertTrue(lit[channel] >= 0.98);
                double linear = LightMath.toLinear(scene[channel]);
                double lum = LightMath.luma(0.4 * brightness[0], 0.4 * brightness[1], 0.4 * brightness[2]);
                double previous = linear / Math.max(lum, 0.02)
                        * Math.max(LightMath.ALBEDO_FLOOR, Math.min(1, lum * 8)) * LightMath.ALBEDO_GAIN;
                double albedo = LightMath.recoverAlbedo(linear, brightness[channel], 1, previous);
                assertTrue(BloomMath.excess(linear, albedo * light[channel]) > 0);
            }
            assertArrayEquals(scene, LightMath.compositeLit(scene, new double[3], 1, brightness, 1), 1.0e-12);
        }
    }
}
