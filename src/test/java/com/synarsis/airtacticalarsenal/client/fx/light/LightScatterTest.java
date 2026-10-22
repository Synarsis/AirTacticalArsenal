package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Soft cone, spill, fill and the shadow disc")
class LightScatterTest {

    private static double cos(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }

    @Test
    @DisplayName("the headlight cone goes from 18 to 38 degrees, squared so the edge is soft")
    void softCone() {
        double inner = cos(LightMath.HEADLIGHT_INNER_DEGREES);
        double outer = cos(LightMath.HEADLIGHT_OUTER_DEGREES);
        assertEquals(1.0, LightMath.coneSoft(1.0, outer, inner), 0.0);
        assertEquals(1.0, LightMath.coneSoft(inner, outer, inner), 1.0e-12);
        assertEquals(0.0, LightMath.coneSoft(outer, outer, inner), 0.0);
        assertEquals(0.0, LightMath.coneSoft(cos(60.0), outer, inner), 0.0);
        double previous = 1.0;
        for (double angle = 18.0; angle <= 38.0; angle += 1.0) {
            double v = LightMath.coneSoft(cos(angle), outer, inner);
            assertTrue(v <= previous + 1.0e-12, "monotone at " + angle);
            previous = v;
        }
        double mid = cos(28.0);
        double linear = LightMath.cone(mid, outer, inner);
        assertEquals(linear * linear, LightMath.coneSoft(mid, outer, inner), 1.0e-12);
        assertEquals(38.0, LightMath.HEADLIGHT_OUTER_DEGREES, 0.0);
        assertEquals(18.0, LightMath.HEADLIGHT_INNER_DEGREES, 0.0);
    }

    @Test
    @DisplayName("outside the cone 4 percent of the light spills, up to 70 degrees from the axis, and nothing beyond")
    void spill() {
        double outer = cos(LightMath.HEADLIGHT_OUTER_DEGREES);
        double inner = cos(LightMath.HEADLIGHT_INNER_DEGREES);
        assertEquals(0.04, LightMath.spill(outer, outer), 1.0e-12);
        assertEquals(0.0, LightMath.spill(cos(70.0), outer), 1.0e-12);
        assertEquals(0.0, LightMath.spill(cos(80.0), outer), 1.0e-12);
        assertEquals(0.0, LightMath.spill(cos(179.0), outer), 1.0e-12);
        assertTrue(LightMath.spill(cos(55.0), outer) > 0.0 && LightMath.spill(cos(55.0), outer) < 0.04);
        assertEquals(1.0, LightMath.spot(1.0, outer, inner), 1.0e-12);
        assertEquals(0.04, LightMath.spot(cos(38.0), outer, inner), 1.0e-12);
        assertEquals(0.0, LightMath.spot(cos(75.0), outer, inner), 1.0e-12);
        for (double angle = 0.0; angle < 90.0; angle += 1.0) {
            assertTrue(LightMath.spot(cos(angle), outer, inner) <= 1.0 + 1.0e-12);
            assertTrue(LightMath.spot(cos(angle + 1.0), outer, inner) <= LightMath.spot(cos(angle), outer, inner) + 1.0e-12,
                    "the light never grows away from the axis, " + angle);
        }
    }

    @Test
    @DisplayName("the fill is 7 percent of the light, gone at half the radius, and only in front of a headlight")
    void fill() {
        assertEquals(0.07, LightMath.fill(0.0, 36.0, 1.0, true), 1.0e-12);
        assertEquals(0.0, LightMath.fill(18.0, 36.0, 1.0, true), 1.0e-12);
        assertEquals(0.0, LightMath.fill(30.0, 36.0, 1.0, true), 1.0e-12);
        assertEquals(0.0, LightMath.fill(5.0, 36.0, -0.5, true), 0.0);
        assertEquals(0.0, LightMath.fill(5.0, 36.0, 0.0, true), 0.0);
        assertTrue(LightMath.fill(5.0, 36.0, 0.1, true) < LightMath.fill(5.0, 36.0, 0.9, true));
        assertEquals(LightMath.fill(5.0, 36.0, 0.9, true), LightMath.fill(5.0, 36.0, -0.9, false), 1.0e-12,
                "a point light fills on every side");
        double previous = 1.0;
        for (double d = 0.0; d <= 18.0; d += 0.5) {
            double v = LightMath.fill(d, 36.0, 1.0, true);
            assertTrue(v <= previous + 1.0e-12, "falls with distance, " + d);
            previous = v;
        }
    }

    @Test
    @DisplayName("the screen noise is steady, spread over 0 to 1, and averages near a half")
    void noise() {
        assertEquals(0.0, LightMath.interleavedGradientNoise(0.0, 0.0), 0.0);
        assertEquals(LightMath.interleavedGradientNoise(123.5, 77.5), LightMath.interleavedGradientNoise(123.5, 77.5), 0.0);
        double sum = 0.0;
        int n = 0;
        double low = 1.0;
        double high = 0.0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                double v = LightMath.interleavedGradientNoise(x + 0.5, y + 0.5);
                assertTrue(v >= 0.0 && v < 1.0);
                sum += v;
                n++;
                low = Math.min(low, v);
                high = Math.max(high, v);
            }
        }
        assertEquals(0.5, sum / n, 0.03);
        assertTrue(low < 0.05 && high > 0.95);
    }

    @Test
    @DisplayName("the disc points stay inside the source disc, differ from each other, and turn with the noise")
    void disc() {
        for (int count : new int[] {1, 2, 4}) {
            for (double noise = 0.0; noise < 1.0; noise += 0.125) {
                for (int i = 0; i < count; i++) {
                    double[] p = LightMath.discPoint(i, count, 0.15, noise);
                    assertTrue(Math.hypot(p[0], p[1]) <= 0.15 + 1.0e-12, count + " " + noise + " " + i);
                }
            }
        }
        double[][] four = new double[4][];
        for (int i = 0; i < 4; i++) {
            four[i] = LightMath.discPoint(i, 4, 1.0, 0.0);
        }
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertTrue(Math.hypot(four[i][0] - four[j][0], four[i][1] - four[j][1]) > 0.3, i + " " + j);
            }
        }
        double[] a = LightMath.discPoint(0, 4, 1.0, 0.0);
        double[] b = LightMath.discPoint(0, 4, 1.0, 0.25);
        assertEquals(Math.hypot(a[0], a[1]), Math.hypot(b[0], b[1]), 1.0e-12);
        assertTrue(Math.hypot(a[0] - b[0], a[1] - b[1]) > 0.1, "the noise turns the pattern");
        double meanX = 0.0;
        double meanY = 0.0;
        int steps = 64;
        for (int s = 0; s < steps; s++) {
            double[] p = LightMath.discPoint(0, 4, 1.0, (double) s / steps);
            meanX += p[0];
            meanY += p[1];
        }
        assertEquals(0.0, meanX / steps, 1.0e-9);
        assertEquals(0.0, meanY / steps, 1.0e-9);
    }
}
