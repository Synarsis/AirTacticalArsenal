package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;

public record PatriotPose(TelHitboxPose.Part supports, TelHitboxPose.Part lift, TelHitboxPose.Part service,
        TelHitboxPose.Part doors, double articulationYaw, double articulationPitch, double articulationRoll,
        double tractorOffsetPx, double latchDegrees, double traverseDegrees, double steerFront, double steerSecond,
        double[] wheelSpin, double[] wheelLift) {

    public static final int TRACTOR_LEFT = 0;
    public static final int TRACTOR_RIGHT = 1;
    public static final int TRAILER_LEFT = 2;
    public static final int TRAILER_RIGHT = 3;

    public static final int TRACTOR_WHEELS = 8;
    public static final int TRAILER_WHEELS = 4;

    private static final double[] NO_SPIN = new double[4];

    public static final PatriotPose REST = new PatriotPose(TelHitboxPose.Part.REST, TelHitboxPose.Part.REST,
            TelHitboxPose.Part.REST, TelHitboxPose.Part.REST, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, NO_SPIN, null);

    public TelHitboxPose.Part part(int group) {
        return switch (group) {
            case PatriotBones.SUPPORTS -> this.supports;
            case PatriotBones.LIFT -> this.lift;
            case PatriotBones.SERVICE -> this.service;
            default -> this.doors;
        };
    }

    public PatriotPose withParts(TelHitboxPose.Part supports, TelHitboxPose.Part lift, TelHitboxPose.Part service,
            TelHitboxPose.Part doors) {
        return new PatriotPose(supports, lift, service, doors, this.articulationYaw, this.articulationPitch,
                this.articulationRoll, this.tractorOffsetPx, this.latchDegrees, this.traverseDegrees, this.steerFront,
                this.steerSecond, this.wheelSpin, this.wheelLift);
    }

    public PatriotPose withArticulation(double yaw, double pitch, double roll) {
        return new PatriotPose(this.supports, this.lift, this.service, this.doors, yaw, pitch, roll,
                this.tractorOffsetPx, this.latchDegrees, this.traverseDegrees, this.steerFront, this.steerSecond,
                this.wheelSpin, this.wheelLift);
    }

    public PatriotPose withTractor(double offsetPx, double latchDegrees) {
        return new PatriotPose(this.supports, this.lift, this.service, this.doors, this.articulationYaw,
                this.articulationPitch, this.articulationRoll, offsetPx, latchDegrees, this.traverseDegrees,
                this.steerFront, this.steerSecond, this.wheelSpin, this.wheelLift);
    }

    public PatriotPose withTraverse(double degrees) {
        return new PatriotPose(this.supports, this.lift, this.service, this.doors, this.articulationYaw,
                this.articulationPitch, this.articulationRoll, this.tractorOffsetPx, this.latchDegrees, degrees,
                this.steerFront, this.steerSecond, this.wheelSpin, this.wheelLift);
    }

    public PatriotPose withSteer(double front, double second) {
        return new PatriotPose(this.supports, this.lift, this.service, this.doors, this.articulationYaw,
                this.articulationPitch, this.articulationRoll, this.tractorOffsetPx, this.latchDegrees,
                this.traverseDegrees, front, second, this.wheelSpin, this.wheelLift);
    }

    public PatriotPose withWheels(double[] spin, double[] lift) {
        return new PatriotPose(this.supports, this.lift, this.service, this.doors, this.articulationYaw,
                this.articulationPitch, this.articulationRoll, this.tractorOffsetPx, this.latchDegrees,
                this.traverseDegrees, this.steerFront, this.steerSecond, spin, lift);
    }

    public static TelHitboxPose.Part partOf(PatriotKind kind, int group, TelPose.Clip clip, double ageTicks) {
        String name = PatriotBones.clipName(kind, group, clip);
        return name == null ? TelHitboxPose.Part.REST
                : new TelHitboxPose.Part(name, TelPose.seconds(clip, ageTicks));
    }

    public double tractorSpinAlongOffset() {
        return PatriotGeometry.TRACTOR_OFFSET_WHEEL_DEGREES * this.tractorOffsetPx / PatriotGeometry.TRACTOR_OFFSET_PX;
    }

    private double spin(int index) {
        return this.wheelSpin == null || index >= this.wheelSpin.length ? 0.0 : this.wheelSpin[index];
    }

    private double lift(int wheel) {
        return this.wheelLift == null || wheel >= this.wheelLift.length ? 0.0 : this.wheelLift[wheel];
    }

    private static int wheelNumber(String bone) {
        return bone.charAt(bone.length() - 1) - '0';
    }

    private static boolean isLeft(String bone) {
        return bone.contains("_left_");
    }

    public ModelHitbox.BonePose bonePose(PatriotKind kind, TelAnimSampler sampler) {
        return (bone, group) -> this.value(kind, sampler, bone, group);
    }

    public TelAnimSampler.BoneValue value(PatriotKind kind, TelAnimSampler sampler, String bone, int group) {
        if (group >= 0) {
            TelHitboxPose.Part part = this.part(group);
            return part.clip() == null ? null : sampler.bone(part.clip(), bone, part.seconds());
        }
        switch (bone) {
            case "root":
                return new TelAnimSampler.BoneValue(
                        new double[] {-this.articulationPitch, this.articulationYaw, this.articulationRoll}, null, null);
            case "tractor_carrier":
                return new TelAnimSampler.BoneValue(null, new double[] {0.0, 0.0, this.tractorOffsetPx}, null);
            case "truck_coupling_latch":
                return new TelAnimSampler.BoneValue(new double[] {0.0, this.latchDegrees, 0.0}, null, null);
            case "traverse":
                return kind.isLauncher()
                        ? new TelAnimSampler.BoneValue(new double[] {0.0, this.traverseDegrees, 0.0}, null, null)
                        : null;
            default:
                break;
        }
        if (bone.startsWith("truck_steer_")) {
            return new TelAnimSampler.BoneValue(
                    new double[] {0.0, wheelNumber(bone) == 3 ? this.steerFront : this.steerSecond, 0.0}, null, null);
        }
        if (bone.startsWith("truck_wheel_")) {
            int wheel = wheelNumber(bone) * 2 + (isLeft(bone) ? 0 : 1);
            double spin = this.spin(isLeft(bone) ? TRACTOR_LEFT : TRACTOR_RIGHT) + this.tractorSpinAlongOffset();
            return new TelAnimSampler.BoneValue(new double[] {-spin, 0.0, 0.0},
                    new double[] {0.0, this.lift(wheel) * 16.0, 0.0}, null);
        }
        if (bone.startsWith("wheel_")) {
            int wheel = TRACTOR_WHEELS + wheelNumber(bone) * 2 + (isLeft(bone) ? 0 : 1);
            return new TelAnimSampler.BoneValue(
                    new double[] {-this.spin(isLeft(bone) ? TRAILER_LEFT : TRAILER_RIGHT), 0.0, 0.0},
                    new double[] {0.0, this.lift(wheel) * 16.0, 0.0}, null);
        }
        return null;
    }
}
