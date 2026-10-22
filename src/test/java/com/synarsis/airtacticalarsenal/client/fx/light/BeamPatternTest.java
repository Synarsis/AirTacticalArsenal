package com.synarsis.airtacticalarsenal.client.fx.light;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeamPatternTest {
    @Test
    void cutoffAndStep() {
        assertTrue(BeamPattern.low(0.0, 2.0) <= 0.03);
        assertEquals(0.423, BeamPattern.low(10.0, 0.5), 0.002);
        assertEquals(0.0101, BeamPattern.low(-10.0, 0.5), 0.0002);
    }

    @Test
    void exactLowCenterAndDeclaredThreshold() {
        assertEquals(0.9292910147212036, BeamPattern.low(2.0, -2.0), 1.0e-12);
        assertTrue(BeamPattern.low(2.0, -2.0) >= 0.92);
    }

    @Test
    void highCenterAndRear() {
        assertEquals(1.0, BeamPattern.high(0.0, 0.0), 0.01);
        assertEquals(0.0, BeamPattern.pattern(2, 0.0, 0.0, -1.0));
        assertEquals(0.0, BeamPattern.pattern(0, 0.0, 0.0, 1.0));
    }

    @Test
    void wallAndGroundPhotometry() {
        assertEquals(232.0, BeamPattern.irradiance(1000, BeamPattern.low(2, -2), 2, 70), 23.2);
        assertEquals(17.5, BeamPattern.irradiance(1000, BeamPattern.low(0, 0), 2, 70), 1.75);
        double[] distances = {4, 8, 15, 50};
        double[] expected = {1.98, 3.18, 2.21, 0.19};
        double[] expectedDiffuse = {0.74, 0.62, 0.235, 0.006};
        for (int index = 0; index < distances.length; index++) {
            double horizontal = distances[index];
            double distance = Math.hypot(horizontal, 1.6);
            double pattern = BeamPattern.pattern(1, 0, -1.6, horizontal);
            double irradiance = BeamPattern.irradiance(1000, pattern, distance, 70);
            assertEquals(expected[index], irradiance, expected[index] * 0.1);
            assertEquals(expectedDiffuse[index], irradiance * 1.6 / distance, expectedDiffuse[index] * 0.1);
        }
    }

    @Test
    void nearSourceFiniteAndWindowEnds() {
        assertTrue(Double.isFinite(BeamPattern.irradiance(200, 1, 0, 70)));
        assertEquals(0, BeamPattern.irradiance(200, 1, 70, 70));
        assertEquals(0, BeamPattern.irradiance(200, 1, 71, 70));
    }

    @Test
    void beamBoundsSmallerThanSphere() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 1.6f, 0.05f, 1000);
        Vector3f position = new Vector3f(0, 0, -200);
        float[] beam = LightBounds.beamRect(projection, position, new Vector3f(0, 0, -1),
                new Vector3f(1, 0, 0), 140, true);
        float[] sphere = LightBounds.screenRect(projection, position, 140);
        assertNotNull(beam);
        assertTrue((beam[2] - beam[0]) * (beam[3] - beam[1]) < (sphere[2] - sphere[0]) * (sphere[3] - sphere[1]));
        for (float coordinate : beam) assertTrue(coordinate >= -1 && coordinate <= 1);
    }

    @Test
    void nearPlaneClippingIsFinite() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 1.6f, 0.05f, 1000);
        float[] beam = LightBounds.beamRect(projection, new Vector3f(0, 0, 1), new Vector3f(0, 0, -1),
                new Vector3f(1, 0, 0), 70, false);
        assertNotNull(beam);
        for (float coordinate : beam) assertTrue(Float.isFinite(coordinate) && Math.abs(coordinate) <= 1);
        assertNull(LightBounds.beamRect(projection, new Vector3f(0, 0, 100), new Vector3f(0, 0, 1),
                new Vector3f(-1, 0, 0), 70, false));
    }
}
