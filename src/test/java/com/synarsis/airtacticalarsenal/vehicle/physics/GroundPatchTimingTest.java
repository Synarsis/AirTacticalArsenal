package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("How long a ground patch takes")
class GroundPatchTimingTest {

    static BlockColumns hills() {
        return (x, z, low, high) -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            double top = 64.0 + Math.floor(3.0 * Math.sin(bx * 0.13) + 3.0 * Math.cos(bz * 0.11) + ((bx * 7 + bz * 13) % 5 == 0 ? 1 : 0));
            return top >= low && top <= high ? top : Double.NaN;
        };
    }

    @Test
    @DisplayName("a full build on hills and steps")
    void fullBuild() {
        BlockColumns columns = hills();
        for (int i = 0; i < 10; i++) {
            GroundPatchBuilder.build(columns, 100.0, 64.0, 100.0);
        }
        double[] millis = new double[25];
        for (int i = 0; i < millis.length; i++) {
            long started = System.nanoTime();
            GroundPatchBuilder.build(columns, 100.0 + i, 64.0, 100.0);
            millis[i] = (System.nanoTime() - started) / 1.0E6;
        }
        Arrays.sort(millis);
        VehicleRig.report(String.format(Locale.ROOT, "TIMING full patch build: median %.2f ms, worst %.2f ms", millis[millis.length / 2], millis[millis.length - 1]));
        assertTrue(millis[millis.length / 2] < 500.0);
    }
}
