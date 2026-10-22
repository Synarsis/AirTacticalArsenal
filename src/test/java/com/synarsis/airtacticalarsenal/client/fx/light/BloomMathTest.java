package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloomMathTest {
    @Test
    void onlyOurOverexposureBlooms() {
        for (double scene : new double[] {0.0, 0.2, 0.9, 1.0}) assertEquals(0, BloomMath.excess(scene, 0));
        assertTrue(BloomMath.excess(0.04, 0.4 * 232) > 0.98);
        assertTrue(BloomMath.excess(0.04, 10000) < 1);
    }
    @Test
    void dualFilterWeightsPreserveConstantAndPositiveSignals() {
        assertEquals(0.6, BloomMath.down(0.6, 0.6, 0.6, 0.6, 0.6), 1e-12);
        assertEquals(0.5, BloomMath.down(1, 0, 0, 0, 0));
        assertEquals(0.125, BloomMath.down(0, 1, 0, 0, 0));
        assertEquals(0.6, BloomMath.up(new double[] {0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6}), 1e-12);
        assertEquals(1.0 / 6, BloomMath.up(new double[] {0, 1, 0, 0, 0, 0, 0, 0}), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> BloomMath.up(new double[4]));
    }
    @Test
    void fourDownFourUpStopAtSixteenth() {
        assertArrayEquals(new int[] {2, 4, 8, 16, 16}, java.util.stream.IntStream.range(0, 5)
                .map(BloomMath::divisor).toArray());
    }
}
