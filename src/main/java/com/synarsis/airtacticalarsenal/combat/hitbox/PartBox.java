package com.synarsis.airtacticalarsenal.combat.hitbox;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PartBox(HitPart part, Vector3f center, Vector3f halfExtents,
                      Quaternionf localRotation, float hpFraction) {

    public static PartBox of(HitPart part,
                             float hx, float hy, float hz,
                             float cx, float cy, float cz,
                             float hpFraction) {
        return new PartBox(part, new Vector3f(cx, cy, cz), new Vector3f(hx, hy, hz),
                new Quaternionf(), hpFraction);
    }

    public static PartBox rotated(HitPart part,
                                  float hx, float hy, float hz,
                                  float cx, float cy, float cz,
                                  float qx, float qy, float qz, float qw,
                                  float hpFraction) {
        return new PartBox(part, new Vector3f(cx, cy, cz), new Vector3f(hx, hy, hz),
                new Quaternionf(qx, qy, qz, qw).normalize(), hpFraction);
    }

    public Vector3f rotatedExtents(Vector3f dest) {
        org.joml.Matrix3f m = localRotation.get(new org.joml.Matrix3f());
        return dest.set(
                Math.abs(m.m00()) * halfExtents.x + Math.abs(m.m10()) * halfExtents.y
                        + Math.abs(m.m20()) * halfExtents.z,
                Math.abs(m.m01()) * halfExtents.x + Math.abs(m.m11()) * halfExtents.y
                        + Math.abs(m.m21()) * halfExtents.z,
                Math.abs(m.m02()) * halfExtents.x + Math.abs(m.m12()) * halfExtents.y
                        + Math.abs(m.m22()) * halfExtents.z);
    }

    public float boundingRadius() {
        float ex = Math.abs(center.x) + halfExtents.x;
        float ey = Math.abs(center.y) + halfExtents.y;
        float ez = Math.abs(center.z) + halfExtents.z;
        return (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
    }
}
