package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlareMathTest {
    @Test
    void outsideBeamIsIdentity() {
        double eye = GlareMath.eye(3000, BeamPattern.pattern(2, 0, 0, -30), 900, 1);
        assertEquals(0, GlareMath.veil(eye, 2, 0));
        assertEquals(1, GlareMath.target(eye));
        assertEquals(1, GlareMath.halo(eye));
    }
    @Test
    void highBeamVeilAndOcclusion() {
        double eye = GlareMath.eye(3000, 1, 900, 1);
        assertEquals(3.333333, eye, 0.00001);
        assertEquals(0.627451, GlareMath.veil(eye, 2, 0), 0.00001);
        assertTrue(GlareMath.veil(eye, 2, 0) >= 20 * GlareMath.veil(eye, 20, 0));
        assertEquals(0, GlareMath.eye(3000, 1, 900, 0));
        assertEquals(0.3 * GlareMath.veil(eye, 2, 0), GlareMath.veil(eye, 2, 1), 1e-12);
    }
    @Test
    void adaptationUsesElapsedTime() {
        double target = GlareMath.target(20);
        assertEquals(0.4, target);
        double once = GlareMath.adapt(1, target, 0.3);
        double split = 1;
        for (int step = 0; step < 30; step++) split = GlareMath.adapt(split, target, 0.01);
        assertEquals(once, split, 1e-12);
        assertTrue(GlareMath.adapt(0.65, 1, 0.3) < 0.8);
        assertTrue(GlareMath.halo(20) > GlareMath.halo(5));
    }
    @Test
    void exposureDarkensToAFloorOfFortyPercent() {
        assertEquals(1.0, GlareMath.target(0.0), 1e-12);
        assertEquals(0.8, GlareMath.target(1.0), 1e-12);
        assertEquals(1.0 / (1.0 + 0.25 * 2.0), GlareMath.target(2.0), 1e-12);
        assertEquals(0.4, GlareMath.target(6.0), 1e-12);
        assertEquals(0.4, GlareMath.target(1000.0), 1e-12);
        double previous = 1.0;
        for (double eye = 0.0; eye < 10.0; eye += 0.25) {
            assertTrue(GlareMath.target(eye) <= previous + 1e-12);
            previous = GlareMath.target(eye);
        }
        assertEquals(1.0 + (0.4 - 1.0) * (1.0 - Math.exp(-1.0)), GlareMath.adapt(1.0, 0.4, 0.3), 1e-12);
        assertEquals(0.4 + (1.0 - 0.4) * (1.0 - Math.exp(-1.0)), GlareMath.adapt(0.4, 1.0, 1.5), 1e-12);
    }

    @Test
    void nineSamplesOnACircleTheSizeOfTheLamp() {
        double[][] points = GlareMath.samples(4.0);
        assertEquals(9, points.length);
        assertEquals(0.0, points[0][0]);
        assertEquals(0.0, points[0][1]);
        for (int index = 1; index < points.length; index++) {
            assertEquals(4.0, Math.hypot(points[index][0], points[index][1]), 1e-12);
        }
        assertEquals(12.0, GlareMath.lampRadiusPixels(0.5, 1.5, 1080.0));
        assertEquals(1.5, GlareMath.lampRadiusPixels(500.0, 1.5, 1080.0));
        assertTrue(GlareMath.lampRadiusPixels(20.0, 1.5, 1080.0) > GlareMath.lampRadiusPixels(40.0, 1.5, 1080.0));
    }

    @Test
    void thePanelOfTheCarAroundTheLampDoesNotHideIt() {
        assertTrue(GlareMath.sampleSees(20.0, 20.0));
        assertTrue(GlareMath.sampleSees(19.75, 20.0));
        assertTrue(GlareMath.sampleSees(19.7, 20.0));
        assertTrue(!GlareMath.sampleSees(19.6, 20.0));
        assertTrue(!GlareMath.sampleSees(5.0, 20.0));
    }

    @Test
    void visibilityEasesOverEightHundredthsOfASecond() {
        assertEquals(1.0 - Math.exp(-1.0), GlareMath.smoothSeen(0.0, 1.0, 0.08), 1e-12);
        double steps = 0.0;
        for (int step = 0; step < 8; step++) steps = GlareMath.smoothSeen(steps, 1.0, 0.01);
        assertEquals(GlareMath.smoothSeen(0.0, 1.0, 0.08), steps, 1e-12);
        assertEquals(0.0, GlareMath.smoothSeen(0.0, 0.0, 0.1), 0.0);
        double flicker = 1.0;
        for (int frame = 0; frame < 60; frame++) {
            flicker = GlareMath.smoothSeen(flicker, frame % 2 == 0 ? 0.0 : 1.0, 1.0 / 60.0);
        }
        assertTrue(flicker > 0.5 && flicker < 1.0, "a 30 Hz flicker settles near a half: " + flicker);
        assertTrue(Math.abs(flicker - 0.5) < 0.2);
    }

    @Test
    void theSmoothingFollowsTheLightNotTheListPosition() {
        java.util.Map<Integer, Integer> slots = new java.util.HashMap<>();
        slots.put(41, 0);
        slots.put(7, 1);
        assertEquals(0.0f, GlareMath.previousSlot(slots, 41));
        assertEquals(1.0f, GlareMath.previousSlot(slots, 7));
        assertEquals(-1.0f, GlareMath.previousSlot(slots, 99));
    }
}
