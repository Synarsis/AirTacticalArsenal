package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;

public final class TractorGeometry {

    public static final double PIVOT_H = 1.0;

    public static final Vec3 COUPLING = new Vec3(0.0, 1.3125, 0.0);
    public static final Vec3 SEAT_DRIVER = new Vec3(-0.7625, 2.159, 8.0);
    public static final Vec3 SEAT_PASSENGER = new Vec3(0.7625, 2.159, 8.0);
    public static final Vec3[] SEATS = {SEAT_DRIVER, SEAT_PASSENGER};
    public static final Vec3[] DISMOUNTS = {
            new Vec3(-2.6, 0.0, 8.0),
            new Vec3(2.6, 0.0, 8.0),
    };

    public static final double HALF_WIDTH = 2.1313;
    public static final double HEIGHT = 4.0438;
    public static final double REAR_Z = -1.7594;
    public static final double FRONT_Z = 10.0938;
    public static final double LENGTH = FRONT_Z - REAR_Z;

    public static final double[] AXLE_Z = {0.0, 1.5, 7.25, 8.75};
    public static final double WHEEL_RADIUS = 0.5875;
    public static final double WHEEL_CENTRE_Y = 0.6375;
    public static final double TRACK_HALF = 1.666;

    public static final float TYPE_WIDTH = 4.3f;
    public static final float TYPE_HEIGHT = 4.05f;

    public static final int SEAT_COUNT = 2;
    public static final int PART_COUNT = 4;
    public static final double[] PART_Z = {-0.875, 2.125, 5.125, 8.125};

    public static final double CULL_HALF_XZ = 12.0;
    public static final double CULL_HEIGHT = 8.0;

    private TractorGeometry() {
    }

    public static Vec3 partPosition(int index, double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg) {
        return TelGeometry.localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg,
                new Vec3(0.0, 0.0, PART_Z[index]));
    }
}
