package com.synarsis.airtacticalarsenal.client.fx.light;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Where a light's sphere falls on the screen")
class LightBoundsTest {

    private static Matrix4f projection() {
        return new Matrix4f().perspective((float) Math.toRadians(70.0), 16.0f / 9.0f, 0.05f, 512.0f);
    }

    @Test
    @DisplayName("a light straight ahead covers a box centred on the screen, and a nearer one covers more")
    void straightAhead() {
        float[] far = LightBounds.screenRect(projection(), new Vector3f(0.0f, 0.0f, -40.0f), 5.0f);
        assertNotNull(far);
        assertEquals(-far[0], far[2], 1.0E-4f);
        assertEquals(-far[1], far[3], 1.0E-4f);
        float[] near = LightBounds.screenRect(projection(), new Vector3f(0.0f, 0.0f, -12.0f), 5.0f);
        assertNotNull(near);
        assertTrue(near[2] - near[0] > far[2] - far[0], "a nearer light is a bigger box");
        assertTrue(far[2] > 0.0f && far[2] < 0.5f, "a small sphere far off is a small box: " + far[2]);
    }

    @Test
    @DisplayName("the box always lies within the screen")
    void withinTheScreen() {
        for (float x : new float[] {-30.0f, -5.0f, 0.0f, 8.0f, 25.0f}) {
            for (float z : new float[] {-6.0f, -15.0f, -60.0f}) {
                float[] r = LightBounds.screenRect(projection(), new Vector3f(x, 2.0f, z), 10.0f);
                if (r == null) {
                    continue;
                }
                assertTrue(r[0] >= -1.0f && r[1] >= -1.0f && r[2] <= 1.0f && r[3] <= 1.0f, "outside at " + x + "," + z);
                assertTrue(r[0] < r[2] && r[1] < r[3], "empty at " + x + "," + z);
            }
        }
    }

    @Test
    @DisplayName("any point of a sphere that is in front of the camera and on the screen falls inside the sphere's box")
    void boxHoldsEverySurfacePoint() {
        java.util.Random random = new java.util.Random(14);
        for (int i = 0; i < 200; i++) {
            float fov = 50.0f + random.nextFloat() * 70.0f;
            float aspect = 1.0f + random.nextFloat() * 1.2f;
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.05f, 512.0f);
            Vector3f centre = new Vector3f((random.nextFloat() - 0.5f) * 200.0f, (random.nextFloat() - 0.5f) * 100.0f,
                    -(random.nextFloat() * 150.0f));
            float radius = 2.0f + random.nextFloat() * 100.0f;
            float[] rect = LightBounds.screenRect(projection, centre, radius);
            for (int k = 0; k < 400; k++) {
                Vector3f dir = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f);
                if (dir.lengthSquared() < 1.0E-6f) {
                    continue;
                }
                dir.normalize(radius);
                Vector3f point = new Vector3f(centre).add(dir);
                if (point.z > -0.06f) {
                    continue;
                }
                org.joml.Vector4f clip = new org.joml.Vector4f(point, 1.0f);
                projection.transform(clip);
                float x = clip.x / clip.w;
                float y = clip.y / clip.w;
                if (x < -1.0f || x > 1.0f || y < -1.0f || y > 1.0f) {
                    continue;
                }
                assertNotNull(rect, "a point of the sphere is on the screen but the sphere has no box (case " + i + ")");
                assertTrue(x >= rect[0] - 1.0E-3f && x <= rect[2] + 1.0E-3f && y >= rect[1] - 1.0E-3f && y <= rect[3] + 1.0E-3f,
                        "a point of the sphere at " + x + "," + y + " is outside its box " + java.util.Arrays.toString(rect)
                                + " (case " + i + ")");
            }
        }
    }

    @Test
    @DisplayName("a light behind the camera, or far off to the side, has no box at all")
    void notSeen() {
        assertNull(LightBounds.screenRect(projection(), new Vector3f(0.0f, 0.0f, 20.0f), 5.0f), "behind");
        assertNull(LightBounds.screenRect(projection(), new Vector3f(500.0f, 0.0f, -20.0f), 5.0f), "far to the side");
    }

    @Test
    @DisplayName("a light whose sphere reaches the camera may light the whole screen")
    void aroundTheCamera() {
        float[] r = LightBounds.screenRect(projection(), new Vector3f(0.0f, 0.0f, -2.0f), 10.0f);
        assertNotNull(r);
        assertEquals(-1.0f, r[0], 0.0f);
        assertEquals(-1.0f, r[1], 0.0f);
        assertEquals(1.0f, r[2], 0.0f);
        assertEquals(1.0f, r[3], 0.0f);
        float[] inside = LightBounds.screenRect(projection(), new Vector3f(0.0f, 0.0f, 3.0f), 10.0f);
        assertNotNull(inside, "a sphere with the camera in it lights something");
    }
}
