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
        assertEquals(0.65, target);
        double once = GlareMath.adapt(1, target, 0.3);
        double split = 1;
        for (int step = 0; step < 30; step++) split = GlareMath.adapt(split, target, 0.01);
        assertEquals(once, split, 1e-12);
        assertTrue(GlareMath.adapt(0.65, 1, 0.3) < 0.8);
        assertTrue(GlareMath.halo(20) > GlareMath.halo(5));
    }
}
