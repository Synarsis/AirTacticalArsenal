package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The headlight fill follows the surface and the shadow")
class HeadlightFillTest {

    private static double wallIrradiance() {
        return BeamPattern.irradiance(1000, BeamPattern.low(2, -2), 2, 70);
    }

    @Test
    @DisplayName("the far face of a block, turned from the lamp, gets nothing")
    void backFace() {
        assertEquals(0.0, LightMath.headlightFill(wallIrradiance(), -1.0, 1.0), 0.0);
        assertEquals(0.0, LightMath.headlightFill(wallIrradiance(), -0.3, 1.0), 1.0e-12);
        assertEquals(0.0, LightMath.headlightFill(wallIrradiance(), -0.9, 0.0), 0.0);
    }

    @Test
    @DisplayName("a wall two blocks from the lamp gets at most 0.05")
    void nearWall() {
        double fill = LightMath.headlightFill(wallIrradiance(), 1.0, 1.0);
        assertTrue(wallIrradiance() > 200.0);
        assertTrue(fill <= 0.05 + 1.0e-12, "fill " + fill);
        assertEquals(0.05, fill, 1.0e-12);
    }

    @Test
    @DisplayName("the ground behind a wall gets no more than 0.3 of what it gets in the open")
    void groundInShadow() {
        for (double horizontal : new double[] {4.0, 8.0, 15.0, 30.0}) {
            double distance = Math.hypot(horizontal, 1.6);
            double pattern = BeamPattern.pattern(1, 0, -1.6, horizontal);
            double irradiance = BeamPattern.irradiance(1000, pattern, distance, 70);
            double normalDotLight = 1.6 / distance;
            double open = LightMath.headlightFill(irradiance, normalDotLight, 1.0);
            double behind = LightMath.headlightFill(irradiance, normalDotLight, 0.0);
            assertTrue(behind <= 0.3 * open + 1.0e-12, "at " + horizontal + ": " + behind + " against " + open);
        }
    }

    @Test
    @DisplayName("a faint beam keeps the proportional part below the cap")
    void proportional() {
        assertEquals(0.02 * 1.0, LightMath.headlightFill(1.0, 1.0, 1.0), 1.0e-12);
        assertTrue(LightMath.headlightFill(1.0, 1.0, 1.0) > LightMath.headlightFill(0.5, 1.0, 1.0));
    }
}
