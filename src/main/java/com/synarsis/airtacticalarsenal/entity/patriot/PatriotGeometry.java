package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;

public final class PatriotGeometry {

    public static final double PIVOT_H = 1.0;

    public static final double[] TRAILER_AXLE_Z = {-4.5, -3.0625};
    public static final double TRAILER_TRACK = 1.5375;
    public static final double TRAILER_WHEEL_RADIUS = 0.531;
    public static final double TRAILER_WHEEL_CENTRE_Y = 0.581;

    public static final double HALF_WIDTH = 2.0;
    public static final double HALF_WIDTH_WITH_FEET = 2.2812;
    public static final double HEIGHT = 4.9375;
    public static final double REAR_Z = -6.375;
    public static final double FRONT_Z = 6.2188;
    public static final double HITCH_LENGTH = FRONT_Z - REAR_Z;

    public static final float TYPE_WIDTH = 4.0f;
    public static final float TYPE_HEIGHT = 4.94f;

    public static final float PART_SIZE = 4.0f;
    public static final double[] PART_Z = {-4.5, -1.5, 1.5, 4.5};

    public static final double CULL_HALF_XZ = 9.0;
    public static final double CULL_HEIGHT = 7.0;

    private PatriotGeometry() {
    }

    public static Vec3 partPosition(int index, double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg) {
        return TelGeometry.localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg,
                new Vec3(0.0, 0.0, PART_Z[index]));
    }
}
