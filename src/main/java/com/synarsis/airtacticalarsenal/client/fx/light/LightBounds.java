package com.synarsis.airtacticalarsenal.client.fx.light;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class LightBounds {

    public static float[] full() {
        return new float[] {-1.0f, -1.0f, 1.0f, 1.0f};
    }

    public static float[] beamRect(Matrix4f projection, Vector3f position, Vector3f direction,
            Vector3f right, float radius, boolean high) {
        Vector3f up = new Vector3f(right).cross(direction).normalize();
        float width = radius * (float) Math.tan(Math.toRadians(high ? 30.0 : 65.0));
        float bottom = -radius * (float) Math.tan(Math.toRadians(high ? 30.0 : 50.0));
        float top = radius * (float) Math.tan(Math.toRadians(high ? 30.0 : 5.0));
        Vector3f[] corners = new Vector3f[8];
        for (int index = 0; index < 8; index++) {
            corners[index] = new Vector3f(position).fma((index & 1) == 0 ? 0.0f : radius, direction)
                    .fma((index & 2) == 0 ? -width : width, right)
                    .fma((index & 4) == 0 ? bottom : top, up);
        }
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int index = 0; index < 8; index++) {
            if (corners[index].z <= -0.05f) projectCorner(projection, corners[index], bounds);
            for (int axis = 1; axis <= 4; axis *= 2) {
                int neighbor = index ^ axis;
                if (neighbor <= index || (corners[index].z < -0.05f) == (corners[neighbor].z < -0.05f)) continue;
                float ratio = (-0.05f - corners[index].z) / (corners[neighbor].z - corners[index].z);
                projectCorner(projection, new Vector3f(corners[index]).lerp(corners[neighbor], ratio), bounds);
            }
        }
        if (bounds[0] > bounds[2] || bounds[2] < -1.0f || bounds[0] > 1.0f
                || bounds[3] < -1.0f || bounds[1] > 1.0f) return null;
        return new float[] {Math.max(-1.0f, bounds[0]), Math.max(-1.0f, bounds[1]),
                Math.min(1.0f, bounds[2]), Math.min(1.0f, bounds[3])};
    }

    private static void projectCorner(Matrix4f projection, Vector3f point, float[] bounds) {
        Vector4f clip = projection.transform(new Vector4f(point, 1.0f));
        float horizontal = clip.x / clip.w;
        float vertical = clip.y / clip.w;
        bounds[0] = Math.min(bounds[0], horizontal);
        bounds[1] = Math.min(bounds[1], vertical);
        bounds[2] = Math.max(bounds[2], horizontal);
        bounds[3] = Math.max(bounds[3], vertical);
    }

    private LightBounds() {
    }

    public static float[] screenRect(Matrix4f projection, Vector3f viewCentre, float radius) {
        if (viewCentre.z - radius >= -0.05f) {
            return null;
        }
        if (viewCentre.z + radius > -0.1f) {
            return full();
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        Vector4f corner = new Vector4f();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    corner.set(viewCentre.x + sx * radius, viewCentre.y + sy * radius, viewCentre.z + sz * radius, 1.0f);
                    projection.transform(corner);
                    if (corner.w <= 1.0E-4f) {
                        return full();
                    }
                    float x = corner.x / corner.w;
                    float y = corner.y / corner.w;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < -1.0f || minX > 1.0f || maxY < -1.0f || minY > 1.0f) {
            return null;
        }
        return new float[] {Math.max(-1.0f, minX), Math.max(-1.0f, minY), Math.min(1.0f, maxX), Math.min(1.0f, maxY)};
    }
}
