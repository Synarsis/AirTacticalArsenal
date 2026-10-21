package com.synarsis.airtacticalarsenal.client.fx.light;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class LightBounds {

    public static float[] full() {
        return new float[] {-1.0f, -1.0f, 1.0f, 1.0f};
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
