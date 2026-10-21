package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;

public final class PatriotGeometry {

    public static final double PIVOT_H = 1.0;

    public static final Vec3 KINGPIN = new Vec3(0.0, 1.341, 5.875);
    public static final Vec3 ARTICULATION_PIVOT = new Vec3(0.0, 1.3125, 5.875);

    public static final double[] TRACTOR_AXLE_Z = {5.875, 7.375, 13.125, 14.625};
    public static final double[] TRAILER_AXLE_Z = {-4.5, -3.0625};
    public static final double TRACTOR_TRACK = 1.666;
    public static final double TRAILER_TRACK = 1.5375;
    public static final double TRACTOR_WHEEL_RADIUS = 0.5875;
    public static final double TRAILER_WHEEL_RADIUS = 0.531;
    public static final double TRACTOR_WHEEL_CENTRE_Y = 0.6375;
    public static final double TRAILER_WHEEL_CENTRE_Y = 0.581;

    public static final int SEAT_COUNT = 2;
    public static final Vec3[] SEATS = {
            new Vec3(-0.7625, 2.159, 13.875),
            new Vec3(0.7625, 2.159, 13.875),
    };
    public static final Vec3[] DISMOUNTS = {
            new Vec3(-2.6, 0.0, 13.9),
            new Vec3(2.6, 0.0, 13.9),
    };

    public static final double HALF_WIDTH = 2.0;
    public static final double HALF_WIDTH_WITH_FEET = 2.28;
    public static final double HEIGHT = 4.94;
    public static final double REAR_Z = -6.4;
    public static final double FRONT_Z = 16.0;
    public static final double HITCH_LENGTH = FRONT_Z - REAR_Z;

    public static final double TRACTOR_OFFSET_PX = 95.0;
    public static final double TRACTOR_OFFSET_BLOCKS = TRACTOR_OFFSET_PX / 16.0;
    public static final double TRACTOR_OFFSET_WHEEL_DEGREES = 561.144;

    public static final float TYPE_WIDTH = 4.0f;
    public static final float TYPE_HEIGHT = 4.94f;

    public static final float PART_SIZE = 4.0f;
    public static final double[] PART_Z = {-5.0, -1.3, 2.4, 6.1, 9.8, 13.5, 17.2, 20.9};

    public static final double CULL_HALF_XZ = 25.0;
    public static final double CULL_HEIGHT = 12.0;

    private PatriotGeometry() {
    }

    public static Vec3 partPosition(int index, double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg) {
        return TelGeometry.localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg,
                new Vec3(0.0, 0.0, PART_Z[index]));
    }
}
