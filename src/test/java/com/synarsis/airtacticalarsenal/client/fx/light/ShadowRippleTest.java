package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Sources of ripple in the headlight spot")
class ShadowRippleTest {

    private static String shader(String name) throws IOException {
        try (InputStream in = ShadowRippleTest.class.getResourceAsStream("/assets/ata/shaders/core/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }

    @Test
    @DisplayName("shadow noise belongs to a texel of the world, not to a pixel of the screen")
    void noiseStaysWithTheWorld() {
        double a = LightMath.shadowNoise(10.01, 64.01, 20.01);
        assertEquals(a, LightMath.shadowNoise(10.05, 64.05, 20.05), 0.0);
        assertEquals(a, LightMath.shadowNoise(10.01 + 128.0, 64.01 + 64.0, 20.01 + 128.0), 0.0);
        assertNotEquals(a, LightMath.shadowNoise(10.07, 64.01, 20.01));
        assertNotEquals(a, LightMath.shadowNoise(10.01, 64.07, 20.01));
        assertNotEquals(a, LightMath.shadowNoise(10.01, 64.01, 20.07));
    }

    @Test
    @DisplayName("shadow noise spreads over 0 to 1 with a mean near a half")
    void noiseSpread() {
        double sum = 0.0;
        double low = 1.0;
        double high = 0.0;
        int count = 0;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                double v = LightMath.shadowNoise(x / 16.0 + 0.01, 70.01, z / 16.0 + 0.01);
                assertTrue(v >= 0.0 && v < 1.0);
                sum += v;
                low = Math.min(low, v);
                high = Math.max(high, v);
                count++;
            }
        }
        assertEquals(0.5, sum / count, 0.06);
        assertTrue(low < 0.1 && high > 0.9);
    }

    @Test
    @DisplayName("a normal within 15 degrees of an axis snaps to it, others stay")
    void snap() {
        double near = Math.toRadians(10.0);
        assertArrayEquals(new double[] {0.0, 1.0, 0.0}, LightMath.snapNormal(unit(Math.sin(near), Math.cos(near), 0.0)), 0.0);
        assertArrayEquals(new double[] {-1.0, 0.0, 0.0},
                LightMath.snapNormal(unit(-Math.cos(near), 0.0, Math.sin(near))), 0.0);
        double far = Math.toRadians(20.0);
        double[] tilted = unit(Math.sin(far), Math.cos(far), 0.0);
        assertArrayEquals(tilted, LightMath.snapNormal(tilted), 1.0e-12);
        double[] diagonal = unit(1.0, 1.0, 0.0);
        assertArrayEquals(diagonal, LightMath.snapNormal(diagonal), 1.0e-12);
        double edge = Math.toRadians(14.9);
        assertArrayEquals(new double[] {0.0, 0.0, 1.0}, LightMath.snapNormal(unit(0.0, Math.sin(edge), Math.cos(edge))), 0.0);
    }

    @Test
    @DisplayName("the shaders no longer take noise from the screen or from the game time")
    void shadersHaveNoScreenOrTimeNoise() throws IOException {
        String volume = shader("ata_light_volume.fsh");
        assertFalse(volume.contains("noiseAt("), "the volume shader still has the screen noise");
        assertFalse(volume.contains("gl_FragCoord"), "the volume shader still reads the pixel");
        assertTrue(volume.contains("shadowNoise("));
        assertTrue(volume.contains("snapNormal("));
        assertTrue(volume.contains("LightCone.z > 0.5 && LightRight.w < 0.5"), "headlights skip the screen contact shadow");
        String beam = shader("ata_light_beam.fsh");
        assertFalse(beam.contains("BeamParams.z"), "the beam jitter still moves with the game time");
    }
}
