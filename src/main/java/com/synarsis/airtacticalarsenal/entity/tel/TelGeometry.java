package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TelGeometry {

    public static final int FILE_X_SIGN = -1;

    public static final double PIVOT_H = 1.0;

    public static final Vec3 OBB_CENTER = new Vec3(0.0, 2.0, 0.36);
    public static final Vec3 OBB_HALF = new Vec3(1.95, 2.0, 7.45);

    public static final float TYPE_WIDTH = 3.9f;
    public static final float TYPE_HEIGHT = 4.0f;

    public static final double CLIMB = 1.3;
    public static final double WALL_BOTTOM = 1.4;
    public static final double HULL_TOP = 4.0;

    public static final double BELLY = 0.55;
    public static final double SKID_X = 1.2;
    public static final double[] SKID_Z = {-7.0, -0.3, 1.6, 7.6};
    public static final double COM_Z = 0.8;

    public static final float PART_SIZE = 4.0f;
    public static final double[] PART_Z = {-5.2, -1.4, 2.4, 6.0};

    public static final double CULL_HALF_XZ = 8.5;
    public static final double CULL_HEIGHT = 11.0;

    public static final int SEAT_COUNT = 3;
    public static final Vec3[] SEATS = {
            new Vec3(1.0625, 2.08, 5.45),
            new Vec3(0.0, 2.08, 5.45),
            new Vec3(-1.0625, 2.08, 5.45),
    };
    public static final Vec3[] DISMOUNTS = {
            new Vec3(2.8, 0.0, 6.2),
            new Vec3(-2.8, 0.0, 6.2),
            new Vec3(-2.8, 0.0, 6.2),
    };

    public static final Vec3[] MISSILE_BASE = {
            new Vec3(FILE_X_SIGN * 0.65625, 1.78125, -5.78125),
            new Vec3(-FILE_X_SIGN * 0.65625, 1.78125, -5.78125),
    };
    public static final double MISSILE_TOP_Y = 9.78125;

    public static final Vec3 ENGINE_DECK = new Vec3(0.0, 3.6, 3.7);

    public static final double WHEEL_X = 27.0 / 16.0;
    public static final double[] WHEEL_Z = {-82.0 / 16.0, -50.0 / 16.0, 48.0 / 16.0, 82.0 / 16.0};

    private TelGeometry() {
    }

    public static Vec3 stowedMissileCentre(int slot) {
        return new Vec3(MISSILE_BASE[slot].x, 2.4, -4.6);
    }

    public static Vec3 fileToLocal(double x, double y, double z) {
        return new Vec3(FILE_X_SIGN * x / 16.0, y / 16.0, z / 16.0);
    }

    public static Vec3 localToWorld(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg,
            Vec3 local) {
        return transform(local.x, local.y, local.z, yawDeg, pitchDeg, rollDeg, true)
                .add(x, y, z);
    }

    public static Vec3 rotateToWorld(float yawDeg, float pitchDeg, float rollDeg, Vec3 localDir) {
        return transform(localDir.x, localDir.y, localDir.z, yawDeg, pitchDeg, rollDeg, false);
    }

    private static Vec3 transform(double lx, double ly, double lz, float yawDeg, float pitchDeg, float rollDeg,
            boolean aboutPivot) {
        double px = lx;
        double py = aboutPivot ? ly - PIVOT_H : ly;
        double pz = lz;

        double roll = Math.toRadians(rollDeg);
        double cr = Math.cos(roll);
        double sr = Math.sin(roll);
        double x1 = px * cr - py * sr;
        double y1 = px * sr + py * cr;

        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double y2 = y1 * cp - pz * sp;
        double z2 = y1 * sp + pz * cp;

        if (aboutPivot) {
            y2 += PIVOT_H;
        }

        double yaw = Math.toRadians(yawDeg);
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        return new Vec3(x1 * cy - z2 * sy, y2, x1 * sy + z2 * cy);
    }

    public static Vec3 collisionCenter(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg) {
        return localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg, OBB_CENTER);
    }

    public static AABB obbWorldAabb(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg) {
        return Obb.fromPose(x, y, z, yawDeg, pitchDeg, rollDeg).worldAabb();
    }

    public static Vec3 partPosition(int index, double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg) {
        return localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg, new Vec3(0.0, 0.0, PART_Z[index]));
    }

    public static Vec3 missileBase(int slot, double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg) {
        return localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg, MISSILE_BASE[slot]);
    }

    public static boolean obbIntersectsAabb(double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg, AABB other, double shrink) {
        return ObbAabbSat.mtv(Obb.fromPose(x, y, z, yawDeg, pitchDeg, rollDeg), other.deflate(shrink)) != null;
    }
}
