package com.synarsis.airtacticalarsenal.combat.hitbox;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class Obb {

    public static final double NO_HIT = -1.0;

    private final HitTag tag;
    private final Vector3f halfExtents = new Vector3f();
    private final Vector3d center = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf inverse = new Quaternionf();

    public Obb(HitTag tag) {
        this.tag = tag;
    }

    public HitTag tag() {
        return this.tag;
    }

    public HitPart part() {
        return this.tag instanceof HitPart part ? part : null;
    }

    public Vector3d center() {
        return this.center;
    }

    public Vector3f halfExtents() {
        return this.halfExtents;
    }

    public Quaternionf rotation() {
        return this.rotation;
    }

    public void place(PartBox template, double originX, double originY, double originZ, Quaternionf rot) {
        this.halfExtents.set(template.halfExtents());
        rot.mul(template.localRotation(), this.rotation);
        this.rotation.conjugate(this.inverse);

        Vector3f offset = rot.transform(template.center().x(), template.center().y(), template.center().z(),
                new Vector3f());
        this.center.set(originX + offset.x, originY + offset.y, originZ + offset.z);
    }

    public void set(double cx, double cy, double cz, float hx, float hy, float hz, Quaternionf rot) {
        this.center.set(cx, cy, cz);
        this.halfExtents.set(hx, hy, hz);
        this.rotation.set(rot);
        this.rotation.conjugate(this.inverse);
    }

    public double clipSegment(Vec3 from, Vec3 to) {
        Vector3f a = this.inverse.transform(
                (float) (from.x - this.center.x), (float) (from.y - this.center.y), (float) (from.z - this.center.z),
                new Vector3f());
        Vector3f b = this.inverse.transform(
                (float) (to.x - this.center.x), (float) (to.y - this.center.y), (float) (to.z - this.center.z),
                new Vector3f());

        double tEnter = 0.0;
        double tExit = 1.0;

        for (int axis = 0; axis < 3; axis++) {
            float origin = a.get(axis);
            float dir = b.get(axis) - origin;
            float half = this.halfExtents.get(axis);

            if (Math.abs(dir) < 1.0E-6f) {
                if (Math.abs(origin) > half) {
                    return NO_HIT;
                }
                continue;
            }

            double t1 = (-half - origin) / dir;
            double t2 = (half - origin) / dir;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }

            if (t1 > tEnter) {
                tEnter = t1;
            }
            if (t2 < tExit) {
                tExit = t2;
            }
            if (tEnter > tExit) {
                return NO_HIT;
            }
        }

        return tEnter;
    }

    public boolean contains(Vec3 point) {
        Vector3f local = this.inverse.transform(
                (float) (point.x - this.center.x), (float) (point.y - this.center.y), (float) (point.z - this.center.z),
                new Vector3f());
        return Math.abs(local.x) <= this.halfExtents.x
                && Math.abs(local.y) <= this.halfExtents.y
                && Math.abs(local.z) <= this.halfExtents.z;
    }

    public AABB worldAabb() {
        Matrix3f m = this.rotation.get(new Matrix3f());
        double ex = Math.abs(m.m00()) * this.halfExtents.x
                + Math.abs(m.m10()) * this.halfExtents.y
                + Math.abs(m.m20()) * this.halfExtents.z;
        double ey = Math.abs(m.m01()) * this.halfExtents.x
                + Math.abs(m.m11()) * this.halfExtents.y
                + Math.abs(m.m21()) * this.halfExtents.z;
        double ez = Math.abs(m.m02()) * this.halfExtents.x
                + Math.abs(m.m12()) * this.halfExtents.y
                + Math.abs(m.m22()) * this.halfExtents.z;

        return new AABB(this.center.x - ex, this.center.y - ey, this.center.z - ez,
                this.center.x + ex, this.center.y + ey, this.center.z + ez);
    }
}
