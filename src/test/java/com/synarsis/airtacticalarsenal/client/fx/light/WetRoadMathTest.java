package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WetRoadMathTest {
    @Test
    void roofDryWeatherAndVerticalWallAreDry() {
        assertEquals(0, WetRoadMath.specular(1, 1, 0.5, 1, 10, false));
        assertEquals(0, WetRoadMath.specular(1, 1, 0.5, 0, 10, true));
        assertEquals(0, WetRoadMath.specular(0.7, 1, 0.5, 1, 10, true));
        for (int index = 0; index < 6; index++) {
            double[] cells = new double[6];
            cells[index] = 1;
            assertFalse(WetRoadMath.open(cells));
        }
        assertTrue(WetRoadMath.open(new double[6]));
    }
    @Test
    void fresnelExponentAndLinearity() {
        assertEquals(0.012, WetRoadMath.specular(1, 1, 1, 1, 1, true), 1e-12);
        assertEquals(0.6, WetRoadMath.specular(1, 1, 0, 1, 1, true), 1e-12);
        double highlight = WetRoadMath.specular(1, 0.9, 0.2, 0.4, 2, true);
        assertTrue(highlight > 0);
        assertEquals(2 * highlight, WetRoadMath.specular(1, 0.9, 0.2, 0.4, 4, true), 1e-12);
    }
}
