package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Lighting arithmetic (SPEC 15.7)")
class LightMathTest {

    @Test
    @DisplayName("attenuation is greatest at the light, exactly 0 at the radius, and falls all the way between")
    void attenuation() {
        double radius = 40.0;
        assertEquals(1.0, LightMath.attenuation(0.0, radius), 1.0E-12);
        assertEquals(0.0, LightMath.attenuation(radius, radius), 0.0);
        assertEquals(0.0, LightMath.attenuation(radius * 1.5, radius), 0.0, "and nothing beyond");
        double last = 1.0;
        for (int i = 1; i <= 400; i++) {
            double a = LightMath.attenuation(radius * i / 400.0, radius);
            assertTrue(a <= last + 1.0E-12, "rose at step " + i);
            assertTrue(a >= 0.0);
            last = a;
        }
        assertEquals(0.0, LightMath.attenuation(5.0, 0.0), 0.0, "a light with no reach lights nothing");
    }

    @Test
    @DisplayName("the core is a quarter of the radius: inside it the light is still strong, well outside it is faint")
    void core() {
        double radius = 40.0;
        double atCore = LightMath.attenuation(radius * LightMath.CORE, radius);
        assertTrue(atCore > 0.4 && atCore < 0.55, "at the core edge " + atCore);
        assertTrue(LightMath.attenuation(radius * 0.8, radius) < 0.1);
        assertTrue(LightMath.attenuation(radius * 0.99, radius) < 0.01);
    }

    @Test
    @DisplayName("the cone is 1 inside the inner angle, 0 beyond the outer one, and rises smoothly between")
    void cone() {
        double cosInner = Math.cos(Math.toRadians(18.0));
        double cosOuter = Math.cos(Math.toRadians(32.0));
        assertEquals(1.0, LightMath.cone(1.0, cosOuter, cosInner), 1.0E-12, "on the axis");
        assertEquals(1.0, LightMath.cone(Math.cos(Math.toRadians(10.0)), cosOuter, cosInner), 1.0E-12);
        assertEquals(1.0, LightMath.cone(cosInner, cosOuter, cosInner), 1.0E-12, "at the inner edge");
        assertEquals(0.0, LightMath.cone(cosOuter, cosOuter, cosInner), 1.0E-12, "at the outer edge");
        assertEquals(0.0, LightMath.cone(Math.cos(Math.toRadians(60.0)), cosOuter, cosInner), 0.0);
        assertEquals(0.0, LightMath.cone(-1.0, cosOuter, cosInner), 0.0, "behind it");
        double last = 1.0;
        for (int degrees = 18; degrees <= 32; degrees++) {
            double v = LightMath.cone(Math.cos(Math.toRadians(degrees)), cosOuter, cosInner);
            assertTrue(v <= last + 1.0E-12, "rose at " + degrees);
            last = v;
        }
        double mid = LightMath.cone(Math.cos(Math.toRadians(25.0)), cosOuter, cosInner);
        assertTrue(mid > 0.1 && mid < 0.9, "soft in between: " + mid);
    }

    @Test
    @DisplayName("shade is plain Lambert with no wrap, and a wrapped light still reaches a surface turned away")
    void shade() {
        assertEquals(1.0, LightMath.shade(0.0, 1.0), 1.0E-12);
        assertEquals(0.5, LightMath.shade(0.0, 0.5), 1.0E-12);
        assertEquals(0.0, LightMath.shade(0.0, -1.0), 1.0E-12);
        assertEquals(0.5, LightMath.shade(0.5, -1.0), 1.0E-12);
        assertEquals(1.0, LightMath.shade(0.5, 1.0), 1.0E-12);
    }

    @Test
    @DisplayName("fog takes the light away over its range and not before")
    void fog() {
        assertEquals(1.0, LightMath.fogKeep(10.0, 100.0, 200.0), 0.0);
        assertEquals(0.5, LightMath.fogKeep(150.0, 100.0, 200.0), 1.0E-12);
        assertEquals(0.0, LightMath.fogKeep(300.0, 100.0, 200.0), 0.0);
        assertEquals(1.0, LightMath.fogKeep(300.0, 200.0, 100.0), 0.0, "a nonsense range is no fog");
    }

    private static final double TAN_X = 1.2;
    private static final double TAN_Y = 0.7;
    private static final double NEAR = 0.05;
    private static final double FAR = 512.0;
    private static final double W = 1920.0;
    private static final double H = 1080.0;

    private static double depthOfZ(double z) {
        double ndcZ = ((FAR + NEAR) + 2.0 * FAR * NEAR / z) / (FAR - NEAR);
        return (ndcZ + 1.0) / 2.0;
    }

    private static double[] onPlane(double u, double v, double base, double sx, double sy) {
        double dx = TAN_X * (2.0 * u - 1.0);
        double dy = TAN_Y * (2.0 * v - 1.0);
        double t = -base / (1.0 + sx * dx + sy * dy);
        return LightMath.viewPosition(u, v, depthOfZ(-t), TAN_X, TAN_Y, NEAR, FAR);
    }

    @Test
    @DisplayName("a position rebuilt from its depth is the point that gave the depth")
    void viewPositionRoundTrip() {
        double u = 0.3;
        double v = 0.8;
        double z = -17.5;
        double[] p = LightMath.viewPosition(u, v, depthOfZ(z), TAN_X, TAN_Y, NEAR, FAR);
        assertEquals(z, p[2], 1.0E-9);
        assertEquals(TAN_X * (2.0 * u - 1.0) * 17.5, p[0], 1.0E-9);
        assertEquals(TAN_Y * (2.0 * v - 1.0) * 17.5, p[1], 1.0E-9);
    }

    @Test
    @DisplayName("the normal from three depths of a tilted plane is the plane's own, to within 0.02")
    void normalOfATiltedPlane() {
        double nx = -0.3;
        double ny = -0.1;
        double nz = 1.0;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        for (double[] uv : new double[][] {{0.5, 0.5}, {0.2, 0.7}, {0.85, 0.3}}) {
            double u = uv[0];
            double v = uv[1];
            double[] c = onPlane(u, v, -10.0, 0.3, 0.1);
            double[] xp = onPlane(u + 1.0 / W, v, -10.0, 0.3, 0.1);
            double[] xn = onPlane(u - 1.0 / W, v, -10.0, 0.3, 0.1);
            double[] yp = onPlane(u, v + 1.0 / H, -10.0, 0.3, 0.1);
            double[] yn = onPlane(u, v - 1.0 / H, -10.0, 0.3, 0.1);
            double[] n = LightMath.normal(c, xp, xn, yp, yn);
            assertEquals(nx / len, n[0], 0.02, "x at " + u + "," + v);
            assertEquals(ny / len, n[1], 0.02, "y at " + u + "," + v);
            assertEquals(nz / len, n[2], 0.02, "z at " + u + "," + v);
        }
    }

    @Test
    @DisplayName("a flat wall facing the camera has the normal straight at it")
    void normalOfAFlatWall() {
        double[] c = onPlane(0.4, 0.6, -20.0, 0.0, 0.0);
        double[] n = LightMath.normal(c, onPlane(0.4 + 1.0 / W, 0.6, -20.0, 0, 0), onPlane(0.4 - 1.0 / W, 0.6, -20.0, 0, 0),
                onPlane(0.4, 0.6 + 1.0 / H, -20.0, 0, 0), onPlane(0.4, 0.6 - 1.0 / H, -20.0, 0, 0));
        assertEquals(0.0, n[0], 0.02);
        assertEquals(0.0, n[1], 0.02);
        assertEquals(1.0, n[2], 0.02);
    }

    @Test
    @DisplayName("at a step in depth the neighbour on the same surface is used, so there is no halo")
    void normalAtAStep() {
        double u = 0.5;
        double v = 0.5;
        double[] c = onPlane(u, v, -10.0, 0.0, 0.0);
        double[] xn = onPlane(u - 1.0 / W, v, -10.0, 0.0, 0.0);
        double[] xp = onPlane(u + 1.0 / W, v, -30.0, 0.0, 0.0);
        double[] yp = onPlane(u, v + 1.0 / H, -10.0, 0.0, 0.0);
        double[] yn = onPlane(u, v - 1.0 / H, -10.0, 0.0, 0.0);
        double[] n = LightMath.normal(c, xp, xn, yp, yn);
        assertTrue(n[2] > 0.99, "it must face the wall it is on, was " + java.util.Arrays.toString(n));

        double[] far = onPlane(u, v, -30.0, 0.0, 0.0);
        double[] left = onPlane(u - 1.0 / W, v, -10.0, 0.0, 0.0);
        double[] right = onPlane(u + 1.0 / W, v, -30.0, 0.0, 0.0);
        double[] up = onPlane(u, v + 1.0 / H, -30.0, 0.0, 0.0);
        double[] down = onPlane(u, v - 1.0 / H, -30.0, 0.0, 0.0);
        double[] nf = LightMath.normal(far, right, left, up, down);
        assertTrue(nf[2] > 0.99, "and the far wall too, was " + java.util.Arrays.toString(nf));
    }

    @Test
    @DisplayName("a normal always faces the camera, whichever way the surface was measured")
    void normalFacesTheCamera() {
        double[] c = {0.0, 0.0, -10.0};
        double[] n = LightMath.normal(c, new double[] {-0.01, 0.0, -10.0}, new double[] {0.01, 0.0, -10.0},
                new double[] {0.0, 0.01, -10.0}, new double[] {0.0, -0.01, -10.0});
        assertTrue(n[2] > 0.99);
    }

    @Test
    @DisplayName("shoulder is the identity up to 0.8, rises steadily above it, and stays below 1")
    void shoulder() {
        for (double x = 0.0; x <= 0.8; x += 0.05) {
            assertEquals(x, LightMath.shoulder(x), 1.0E-12);
        }
        assertEquals(0.8, LightMath.shoulder(0.8), 1.0E-12, "and it is continuous at the knee");
        double last = 0.8;
        for (double x = 0.81; x <= 5.0; x += 0.01) {
            double s = LightMath.shoulder(x);
            assertTrue(s >= last, "fell at " + x);
            assertTrue(s < 1.0, "reached white at " + x);
            last = s;
        }
        assertEquals(0.0442, LightMath.shoulder(0.85) - 0.8, 0.0005);
    }

    @Test
    @DisplayName("daylight washes a light out to 0.5 in the open at noon, and not at night or in a cave")
    void dayFactor() {
        assertEquals(0.5, LightMath.dayFactor(LightMath.daylight(1.0, 15.0)), 1.0E-12, "noon in the open");
        assertEquals(1.0, LightMath.dayFactor(LightMath.daylight(0.0, 15.0)), 1.0E-12, "night");
        assertEquals(1.0, LightMath.dayFactor(LightMath.daylight(1.0, 0.0)), 1.0E-12, "noon, in a cave");
        double half = LightMath.dayFactor(LightMath.daylight(0.5, 15.0));
        assertEquals(0.75, half, 1.0E-12);
        assertTrue(LightMath.dayFactor(LightMath.daylight(1.0, 7.0)) > 0.5, "dim sky light, less wash");
    }

    @Test
    @DisplayName("the sky's brightness follows the sun as the game's own darkening does, and rain dims it")
    void skyBrightness() {
        assertEquals(1.0, LightMath.skyBrightness(0.0, 0.0, true), 1.0E-12, "noon");
        assertEquals(0.0, LightMath.skyBrightness(0.5, 0.0, true), 1.0E-12, "midnight");
        assertEquals(0.2, LightMath.skyBrightness(0.25, 0.0, true), 1.0E-12, "dusk");
        assertEquals(1.0 - 5.0 / 16.0, LightMath.skyBrightness(0.0, 1.0, true), 1.0E-12, "noon in a downpour");
        assertEquals(0.0, LightMath.skyBrightness(0.0, 0.0, false), 0.0, "no sky in the Nether");
    }

    @Test
    @DisplayName("with no light the picture is exactly as it was, bright or dark")
    void noLightNoChange() {
        for (double[] scene : new double[][] {{0.5, 0.5, 0.5}, {1.0, 1.0, 1.0}, {0.9, 0.3, 0.1}, {0.02, 0.02, 0.02}, {0.0, 0.0, 0.0}}) {
            double[] out = LightMath.composite(scene, new double[] {0.0, 0.0, 0.0}, 1.0);
            for (int i = 0; i < 3; i++) {
                assertEquals(scene[i], out[i], 1.0E-9, "channel " + i + " of " + java.util.Arrays.toString(scene));
            }
            double[] unscaled = LightMath.composite(scene, new double[] {5.0, 5.0, 5.0}, 0.0);
            for (int i = 0; i < 3; i++) {
                assertEquals(scene[i], unscaled[i], 1.0E-9, "and with the light scaled to nothing");
            }
        }
    }

    @Test
    @DisplayName("light brightens a surface steadily, keeps its colour, and does not take a dark surface to white")
    void lightBrightens() {
        double[] stone = {0.45, 0.42, 0.40};
        double[] last = stone;
        for (double strength : new double[] {0.25, 0.5, 1.0, 2.0, 4.0, 8.0}) {
            double[] out = LightMath.composite(stone, new double[] {strength, strength, strength}, 1.0);
            for (int i = 0; i < 3; i++) {
                assertTrue(out[i] >= last[i] - 1.0E-12, "dimmer with more light at " + strength);
                assertTrue(out[i] < 1.0, "white at light " + strength);
            }
            assertTrue(out[0] >= out[1] && out[1] >= out[2], "the colour's order survives at " + strength);
            last = out;
        }
        double[] a = LightMath.composite(new double[] {0.30, 0.30, 0.30}, new double[] {3.0, 3.0, 3.0}, 1.0);
        double[] b = LightMath.composite(new double[] {0.55, 0.55, 0.55}, new double[] {3.0, 3.0, 3.0}, 1.0);
        assertTrue(b[0] > a[0], "the lighter block is still lighter");
    }

    @Test
    @DisplayName("a coloured light tints a grey surface its own colour and a strong one does not wash it out")
    void colouredLight() {
        double[] grey = {0.5, 0.5, 0.5};
        double[] out = LightMath.composite(grey, new double[] {3.0, 1.5, 0.4}, 1.0);
        assertTrue(out[0] > out[1] && out[1] > out[2], "warm light makes it warm");
        assertTrue(out[2] >= grey[2] - 1.0E-12, "and nothing gets darker");
        assertTrue(out[0] < 1.0);
    }

    @Test
    @DisplayName("the composite's constants are the SPEC's (revision 14), and the shader has the very same ones")
    void shaderConstantsMatch() throws java.io.IOException {
        assertEquals(0.75, LightMath.ALBEDO_GAIN, 0.0);
        assertEquals(0.4, LightMath.ALBEDO_FLOOR, 0.0);
        assertEquals(0.5, LightMath.DAY_STRENGTH, 0.0);
        assertEquals(0.8, LightMath.KNEE, 0.0, "the shoulder is not touched: no white-out");
        assertEquals(0.2, LightMath.SHOULDER_WIDTH, 0.0);
        String shader = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "resources", "assets", "ata",
                "shaders", "core", "ata_light_composite.fsh"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(shader.contains("const float ALBEDO_GAIN = 0.75;"), "shader gain");
        assertTrue(shader.contains("const float ALBEDO_FLOOR = 0.4;"), "shader floor");
        assertTrue(shader.contains("const float KNEE = 0.8;") && shader.contains("const float SHOULDER_WIDTH = 0.2;"));
    }

    @Test
    @DisplayName("daylight scales the light the same way: at noon the same light adds less than at night")
    void dayScalesLight() {
        double[] scene = {0.4, 0.4, 0.4};
        double[] light = {4.0, 4.0, 4.0};
        double night = LightMath.composite(scene, light, LightMath.dayFactor(0.0))[0];
        double day = LightMath.composite(scene, light, LightMath.dayFactor(1.0))[0];
        assertTrue(night > day && day > scene[0]);
    }
}
