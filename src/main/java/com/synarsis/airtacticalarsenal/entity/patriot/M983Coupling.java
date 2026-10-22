package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class M983Coupling {

    public static final double SADDLE_X = 0.0;
    public static final double SADDLE_Y = 1.3125;
    public static final double SADDLE_Z = 0.0;
    public static final double KINGPIN_X = 0.0;
    public static final double KINGPIN_Y = 1.3125;
    public static final double KINGPIN_Z = 5.875;
    public static final double HINT_RANGE = 8.0;
    public static final double CAPTURE_DISTANCE = 0.35;
    public static final double CAPTURE_HEIGHT = 0.3;
    public static final double CAPTURE_YAW = 25.0;
    public static final double CAPTURE_SPEED_MS = 3.0 / 3.6;
    public static final int CAPTURE_TICKS = 10;
    public static final double UNCOUPLE_SPEED_MS = 0.3;
    public static final double RECOUPLE_DISTANCE = 1.5;

    private M983Coupling() {
    }

    public static double[] saddleAnchor() {
        return new double[] {SADDLE_X, SADDLE_Y, SADDLE_Z};
    }

    public static double[] kingpinAnchor() {
        return new double[] {KINGPIN_X, KINGPIN_Y, KINGPIN_Z};
    }

    public static Vec3 worldOf(double x, double y, double z, float yaw, float pitch, float roll, double[] local) {
        double[][] r = PoseMath.rotation(yaw, pitch, roll);
        double[] origin = PoseMath.bodyOrigin(x, y, z, yaw, pitch, roll);
        return new Vec3(origin[0] + r[0][0] * local[0] + r[0][1] * local[1] + r[0][2] * local[2],
                origin[1] + r[1][0] * local[0] + r[1][1] * local[1] + r[1][2] * local[2],
                origin[2] + r[2][0] * local[0] + r[2][1] * local[1] + r[2][2] * local[2]);
    }

    public static Vec3 entityPosForBodyOrigin(double[] bodyOrigin, float yaw, float pitch, float roll) {
        double[][] r = PoseMath.rotation(yaw, pitch, roll);
        return new Vec3(bodyOrigin[0] + r[0][1], bodyOrigin[1] - 1.0 + r[1][1], bodyOrigin[2] + r[2][1]);
    }

    public static Vec3 saddleWorld(M983TractorEntity tractor) {
        return worldOf(tractor.getX(), tractor.getY(), tractor.getZ(), tractor.getYRot(), tractor.getXRot(),
                tractor.roll, saddleAnchor());
    }

    public static Vec3 kingpinWorld(PatriotVehicleEntity trailer) {
        return worldOf(trailer.getX(), trailer.getY(), trailer.getZ(), trailer.getYRot(), trailer.getXRot(),
                trailer.getRoll(), kingpinAnchor());
    }

    public static float trailerYaw(float tractorYaw, double bendDegrees) {
        return (float) PoseMath.wrapDegrees(tractorYaw + bendDegrees);
    }

    public static double bend(float tractorYaw, float trailerYaw) {
        return PoseMath.wrapDegrees(trailerYaw - tractorYaw);
    }

    public enum Verdict {
        READY,
        FAR,
        HEIGHT,
        ANGLE
    }

    public static Verdict verdict(double dx, double dy, double dz, double bendDegrees) {
        if (Math.hypot(dx, dz) > CAPTURE_DISTANCE) {
            return Verdict.FAR;
        }
        if (Math.abs(dy) > CAPTURE_HEIGHT) {
            return Verdict.HEIGHT;
        }
        if (Math.abs(bendDegrees) > CAPTURE_YAW) {
            return Verdict.ANGLE;
        }
        return Verdict.READY;
    }

    public enum Uncouple {
        OK(null, null),
        NOT_COUPLED("gui.ata.m983.unlock.why", null),
        MOVING("gui.ata.m983.unlock.moving", "message.ata.m983.unlock.moving"),
        SUPPORTS("gui.ata.m983.unlock.supports", "message.ata.m983.unlock.supports");

        private final String panelKey;
        private final String messageKey;

        Uncouple(String panelKey, String messageKey) {
            this.panelKey = panelKey;
            this.messageKey = messageKey;
        }

        @Nullable
        public String panelKey() {
            return this.panelKey;
        }

        @Nullable
        public String messageKey() {
            return this.messageKey;
        }
    }

    public static Uncouple uncouple(boolean coupled, boolean supportsOut, boolean busy, double speedMs) {
        if (!coupled) {
            return Uncouple.NOT_COUPLED;
        }
        if (Math.abs(speedMs) > UNCOUPLE_SPEED_MS) {
            return Uncouple.MOVING;
        }
        if (!supportsOut || busy) {
            return Uncouple.SUPPORTS;
        }
        return Uncouple.OK;
    }

    public static boolean slowEnough(double speedMs) {
        return Math.abs(speedMs) <= CAPTURE_SPEED_MS;
    }

    public static boolean heldStill(int slowTicks) {
        return slowTicks >= CAPTURE_TICKS;
    }

    public static boolean trailerAvailable(boolean destroyed, boolean removed, boolean hitched, boolean overturned,
            float health, boolean supportsOut, boolean busy, boolean hasPassengers) {
        return !destroyed && !removed && !hitched && !overturned && health > 0.0f && supportsOut && !busy
                && !hasPassengers;
    }

    public static Verdict verdict(M983TractorEntity tractor, PatriotVehicleEntity trailer) {
        Vec3 saddle = saddleWorld(tractor);
        Vec3 kingpin = kingpinWorld(trailer);
        return verdict(kingpin.x - saddle.x, kingpin.y - saddle.y, kingpin.z - saddle.z,
                bend(tractor.getYRot(), trailer.getYRot()));
    }

    public static boolean canCapture(M983TractorEntity tractor, PatriotVehicleEntity trailer) {
        return trailer.canBeCoupledNow() && verdict(tractor, trailer) == Verdict.READY;
    }

    public record Hint(int trailerId, double distance, double lateral, double height, double angle,
            Verdict verdict, boolean ready) {
    }

    @Nullable
    public static Hint hint(M983TractorEntity tractor) {
        PatriotVehicleEntity best = null;
        double bestDistance = HINT_RANGE;
        Vec3 saddle = saddleWorld(tractor);
        double[][] r = PoseMath.rotation(tractor.getYRot(), tractor.getXRot(), tractor.roll);
        double[] forward = PoseMath.apply(r, new double[] {0.0, 0.0, 1.0});
        for (PatriotVehicleEntity trailer : tractor.level().getEntitiesOfClass(PatriotVehicleEntity.class,
                tractor.getBoundingBox().inflate(HINT_RANGE))) {
            if (!trailer.canBeCoupledNow()) {
                continue;
            }
            Vec3 kingpin = kingpinWorld(trailer);
            double dx = kingpin.x - saddle.x;
            double dz = kingpin.z - saddle.z;
            double distance = Math.hypot(dx, dz);
            if (distance < bestDistance && dx * forward[0] + dz * forward[2] <= CAPTURE_DISTANCE) {
                bestDistance = distance;
                best = trailer;
            }
        }
        if (best == null) {
            return null;
        }
        Vec3 kingpin = kingpinWorld(best);
        double[] side = PoseMath.apply(r, new double[] {1.0, 0.0, 0.0});
        double dx = kingpin.x - saddle.x;
        double dz = kingpin.z - saddle.z;
        double lateral = dx * side[0] + dz * side[2];
        double angle = Math.abs(bend(tractor.getYRot(), best.getYRot()));
        Verdict verdict = verdict(dx, kingpin.y - saddle.y, dz, bend(tractor.getYRot(), best.getYRot()));
        return new Hint(best.getId(), bestDistance, lateral, kingpin.y - saddle.y, angle, verdict,
                verdict == Verdict.READY);
    }

    public static double[] trailerPoseFor(double x, double y, double z, float tractorYaw, float pitch, float roll,
            double bendDegrees) {
        float yaw = trailerYaw(tractorYaw, bendDegrees);
        Vec3 saddle = worldOf(x, y, z, tractorYaw, pitch, roll, saddleAnchor());
        double[][] r = PoseMath.rotation(yaw, pitch, roll);
        double[] kingpin = PoseMath.apply(r, kingpinAnchor());
        double[] bodyOrigin = new double[] {saddle.x - kingpin[0], saddle.y - kingpin[1], saddle.z - kingpin[2]};
        Vec3 origin = entityPosForBodyOrigin(bodyOrigin, yaw, pitch, roll);
        return new double[] {origin.x, origin.y, origin.z, yaw, pitch, roll};
    }

    public static void placeTrailer(PatriotVehicleEntity trailer, double[] pose, double bendDegrees) {
        trailer.setPos(pose[0], pose[1], pose[2]);
        trailer.setYRot((float) pose[3]);
        trailer.setXRot((float) pose[4]);
        trailer.setRoll((float) pose[5]);
        trailer.setArticulation((float) bendDegrees, 0.0f, 0.0f);
        trailer.setDeltaMovement(Vec3.ZERO);
    }

    public static void applyTrailerPose(PatriotVehicleEntity trailer, M983TractorEntity tractor,
            double bendDegrees) {
        placeTrailer(trailer, trailerPoseFor(tractor.getX(), tractor.getY(), tractor.getZ(), tractor.getYRot(),
                tractor.getXRot(), tractor.roll, bendDegrees), bendDegrees);
    }
}
