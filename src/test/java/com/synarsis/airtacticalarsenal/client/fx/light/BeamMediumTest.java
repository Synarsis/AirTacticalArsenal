package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeamMediumTest {
    @Test
    void phaseIntegratesToOneAndFacesForward() {
        double integral = 0;
        int count = 100000;
        for (int index = 0; index < count; index++) integral += BeamMath.phase(-1 + (index + 0.5) * 2 / count);
        integral *= 4 * Math.PI / count;
        assertEquals(1, integral, 0.01);
        assertTrue(BeamMath.phase(1) > BeamMath.phase(-1));
    }
    @Test
    void fakeWorldWallAtTen() {
        for (int mode = 1; mode <= 2; mode++) {
            float[] ranges = BeamRangeMap.build(mode, 70, (direction, maximum) -> Math.min(maximum, 10 / direction.z));
            assertEquals(128, ranges.length);
            int centre = (mode == 1 ? 7 : 4) * 16 + 8;
            assertEquals(10, ranges[centre], 0.5);
            float[] empty = BeamRangeMap.build(mode, 70, (direction, maximum) -> maximum);
            for (float range : empty) assertEquals(70, range);
        }
    }
    @Test
    void sixteenSamplesIntegrateAttenuatedLightOnlyBeforeSurface() {
        int[] samples = {0};
        double actual = BeamMath.integrate(2, 12, 0.5, 0.04, 0.3, distance -> {
            samples[0]++;
            assertTrue(distance > 2 && distance < 12);
            return 3;
        });
        assertEquals(16, samples[0]);
        double analytic = 3 * BeamMath.phase(0.3) * (Math.exp(-0.04 * 2) - Math.exp(-0.04 * 12));
        assertEquals(analytic, actual, analytic * 0.001);
        assertEquals(0, BeamMath.integrate(12, 2, 0.5, 0.04, 0.3, distance -> fail("Empty segment sampled")));
        assertEquals(0, BeamMath.integrate(0, 12, 0.5, 0.04, 0.3, distance -> 0));
    }
    @Test
    void weatherAndUpsampleWeights() {
        assertTrue(BeamMath.density(1, 0, 128, false) > BeamMath.density(0, 0, 128, false));
        assertTrue(BeamMath.density(1, 0, 128, true) > BeamMath.density(1, 0, 128, false));
        assertEquals(1, BeamMath.bilateral(10, 10));
        assertTrue(BeamMath.bilateral(10, 20) < 1e-8);
    }
}
