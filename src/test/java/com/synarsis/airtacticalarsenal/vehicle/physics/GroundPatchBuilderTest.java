package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Ground patch under a vehicle")
class GroundPatchBuilderTest {

    private interface Surface {
        double top(int x, int z);
    }

    private static BlockColumns columns(Surface surface) {
        return (x, z, low, high) -> {
            double top = surface.top((int) Math.floor(x), (int) Math.floor(z));
            return top >= low && top <= high ? top : Double.NaN;
        };
    }

    private static GroundPatch build(Surface surface) {
        return GroundPatchBuilder.build(columns(surface), 100.0, 64.0, 100.0);
    }

    @Test
    @DisplayName("flat ground gives a flat patch of 128 by 128 nodes covering about 64 metres")
    void flat() {
        GroundPatch patch = build((x, z) -> 64.0);
        assertEquals(128, patch.count());
        assertEquals(64.0, patch.heightAt(100.0, 100.0), 1.0e-6);
        assertEquals(0.0, patch.steepestSlopeDegrees(), 0.0);
        assertTrue(patch.covers(100.0, 100.0));
        assertTrue(patch.covers(70.0, 130.0));
        assertFalse(patch.covers(60.0, 100.0));
        assertEquals(0.5, patch.step(), 0.0);
        assertEquals(100.0, patch.centreX(), 0.5);
    }

    @Test
    @DisplayName("nodes sit on the middles of half-block cells, so one block has two nodes")
    void nodePlacement() {
        GroundPatch patch = build((x, z) -> x >= 100 ? 65.0 : 64.0);
        for (int ix = 0; ix < patch.count(); ix++) {
            double fraction = patch.nodeX(ix) - Math.floor(patch.nodeX(ix));
            assertTrue(Math.abs(fraction - 0.25) < 1.0e-9 || Math.abs(fraction - 0.75) < 1.0e-9, "node " + patch.nodeX(ix));
        }
    }

    @Test
    @DisplayName("a one block step becomes a ramp no steeper than 34 degrees, and its top is untouched")
    void stepBecomesRamp() {
        GroundPatch patch = build((x, z) -> x >= 100 ? 65.0 : 64.0);
        assertTrue(patch.steepestSlopeDegrees() <= 34.0, "slope " + patch.steepestSlopeDegrees());
        assertTrue(patch.steepestSlopeDegrees() > 25.0, "there should be a ramp, not a flat floor");
        assertEquals(65.0, patch.heightAt(101.0, 100.0), 1.0e-6);
        assertEquals(64.0, patch.heightAt(95.0, 100.0), 1.0e-6);
        double midway = patch.heightAt(99.0, 100.0);
        assertTrue(midway > 64.2 && midway < 65.0, "ramp at the foot of the step: " + midway);
        double rampLength = 0.0;
        for (double x = 99.9; x > 90.0; x -= 0.05) {
            if (patch.heightAt(x, 100.0) > 64.001) {
                rampLength = 100.0 - x;
            }
        }
        assertTrue(rampLength > 1.2 && rampLength < 2.2, "ramp length " + rampLength);
    }

    @Test
    @DisplayName("a wall of three blocks stays a cliff")
    void wallStaysCliff() {
        GroundPatch patch = build((x, z) -> x >= 100 ? 67.0 : 64.0);
        assertTrue(patch.steepestSlopeDegrees() > 70.0, "slope " + patch.steepestSlopeDegrees());
        assertEquals(64.0, patch.heightAt(99.5, 100.0), 1.0e-6);
        assertEquals(67.0, patch.heightAt(100.5, 100.0), 1.0e-6);
    }

    @Test
    @DisplayName("a step of exactly 1.2 is softened and one a little higher is not")
    void thresholdOfTheStep() {
        GroundPatch soft = build((x, z) -> x >= 100 ? 65.2 : 64.0);
        assertTrue(soft.steepestSlopeDegrees() < 34.0);
        GroundPatch hard = build((x, z) -> x >= 100 ? 65.3 : 64.0);
        assertTrue(hard.steepestSlopeDegrees() > 60.0);
    }

    @Test
    @DisplayName("a bridge above the roof is not in the patch, a ceiling below it is")
    void windowAbove() {
        GroundPatch bridge = build((x, z) -> 64.0);
        BlockColumns withBridge = (x, z, low, high) -> {
            double deck = 70.0;
            if (deck >= low && deck <= high) {
                return deck;
            }
            return 64.0;
        };
        GroundPatch under = GroundPatchBuilder.build(withBridge, 100.0, 64.0, 100.0);
        assertEquals(64.0, under.heightAt(100.0, 100.0), 1.0e-6);
        assertEquals(bridge.heightAt(100.0, 100.0), under.heightAt(100.0, 100.0), 1.0e-6);

        BlockColumns lowCeiling = (x, z, low, high) -> {
            double deck = 67.5;
            if (deck >= low && deck <= high) {
                return deck;
            }
            return 64.0;
        };
        GroundPatch tunnel = GroundPatchBuilder.build(lowCeiling, 100.0, 64.0, 100.0);
        assertEquals(67.5, tunnel.heightAt(100.0, 100.0), 1.0e-6);
    }

    @Test
    @DisplayName("ground deeper than 8 blocks below is a hole, and a hole is never filled by the ramp")
    void holes() {
        GroundPatch pit = build((x, z) -> x >= 100 && x < 104 ? 50.0 : 64.0);
        assertTrue(Double.isNaN(pit.heightAt(101.0, 100.0)));
        assertEquals(64.0, pit.heightAt(95.0, 100.0), 1.0e-6);
        assertEquals(64.0, pit.heightAt(106.0, 100.0), 1.0e-6);
    }

    @Test
    @DisplayName("a rebuild is wanted after 8 metres from the centre, after a block change, or after a fall")
    void rebuildRules() {
        GroundPatch patch = build((x, z) -> 64.0);
        assertFalse(GroundPatchBuilder.needsRebuild(patch, 103.0, 64.0, 99.0, false));
        assertTrue(GroundPatchBuilder.needsRebuild(patch, 109.0, 64.0, 100.0, false));
        assertTrue(GroundPatchBuilder.needsRebuild(patch, 100.0, 64.0, 91.0, false));
        assertTrue(GroundPatchBuilder.needsRebuild(patch, 100.0, 70.0, 100.0, false));
        assertTrue(GroundPatchBuilder.needsRebuild(patch, 100.0, 64.0, 100.0, true));
        assertTrue(GroundPatchBuilder.needsRebuild(null, 0, 0, 0, false));
    }

    @Test
    @DisplayName("a block change counts when it is inside the patch, not when it is far outside")
    void changeInsidePatch() {
        GroundPatch patch = build((x, z) -> 64.0);
        assertTrue(GroundPatchBuilder.touches(patch, 100, 100));
        assertTrue(GroundPatchBuilder.touches(patch, 69, 130));
        assertFalse(GroundPatchBuilder.touches(patch, 20, 100));
        assertFalse(GroundPatchBuilder.touches(patch, 100, 200));
    }
}
