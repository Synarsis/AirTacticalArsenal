package com.synarsis.airtacticalarsenal.entity.tel.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Obb {

    private final double cx;
    private final double cy;
    private final double cz;
    private final Vec3[] axes;
    private final double[] half;

    private Obb(double cx, double cy, double cz, Vec3[] axes, double[] half) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.axes = axes;
        this.half = half;
    }

    public static Obb of(Vec3 centre, Vec3 ax, Vec3 ay, Vec3 az, double hx, double hy, double hz) {
        return new Obb(centre.x, centre.y, centre.z, new Vec3[] {ax, ay, az}, new double[] {hx, hy, hz});
    }

    public static Obb fromPose(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg) {
        Vec3 centre = TelGeometry.collisionCenter(x, y, z, yawDeg, pitchDeg, rollDeg);
        return of(centre,
                TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(1, 0, 0)),
                TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(0, 1, 0)),
                TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(0, 0, 1)),
                TelGeometry.OBB_HALF.x, TelGeometry.OBB_HALF.y, TelGeometry.OBB_HALF.z);
    }

    public static Obb wallHull(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg) {
        double bottom = TelGeometry.WALL_BOTTOM;
        double half = (TelGeometry.HULL_TOP - bottom) * 0.5;
        Vec3 local = new Vec3(TelGeometry.OBB_CENTER.x, bottom + half, TelGeometry.OBB_CENTER.z);
        return of(TelGeometry.localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg, local),
                TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(1, 0, 0)),
                TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(0, 1, 0)),
                TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(0, 0, 1)),
                TelGeometry.OBB_HALF.x, half, TelGeometry.OBB_HALF.z);
    }

    public Vec3 centre() {
        return new Vec3(this.cx, this.cy, this.cz);
    }

    public Vec3 axis(int i) {
        return this.axes[i];
    }

    public double half(int i) {
        return this.half[i];
    }

    public Obb translated(double dx, double dy, double dz) {
        return new Obb(this.cx + dx, this.cy + dy, this.cz + dz, this.axes, this.half);
    }

    public Obb translated(Vec3 d) {
        return this.translated(d.x, d.y, d.z);
    }

    public AABB worldAabb() {
        double ex = 0.0;
        double ey = 0.0;
        double ez = 0.0;
        for (int i = 0; i < 3; i++) {
            ex += Math.abs(this.axes[i].x) * this.half[i];
            ey += Math.abs(this.axes[i].y) * this.half[i];
            ez += Math.abs(this.axes[i].z) * this.half[i];
        }
        return new AABB(this.cx - ex, this.cy - ey, this.cz - ez, this.cx + ex, this.cy + ey, this.cz + ez);
    }

    public Vec3 point(double a, double b, double c) {
        return new Vec3(
                this.cx + this.axes[0].x * a + this.axes[1].x * b + this.axes[2].x * c,
                this.cy + this.axes[0].y * a + this.axes[1].y * b + this.axes[2].y * c,
                this.cz + this.axes[0].z * a + this.axes[1].z * b + this.axes[2].z * c);
    }
}
