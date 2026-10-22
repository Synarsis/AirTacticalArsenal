package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;

public record PatriotPose(TelHitboxPose.Part supports, TelHitboxPose.Part lift, TelHitboxPose.Part service,
        double traverseDegrees, double wheelSpinLeft, double wheelSpinRight) {

    public static final PatriotPose REST = new PatriotPose(TelHitboxPose.Part.REST, TelHitboxPose.Part.REST,
            TelHitboxPose.Part.REST, 0.0, 0.0, 0.0);

    public TelHitboxPose.Part part(int group) {
        return switch (group) {
            case PatriotBones.SUPPORTS -> this.supports;
            case PatriotBones.LIFT -> this.lift;
            default -> this.service;
        };
    }

    public PatriotPose withParts(TelHitboxPose.Part supports, TelHitboxPose.Part lift, TelHitboxPose.Part service) {
        return new PatriotPose(supports, lift, service, this.traverseDegrees, this.wheelSpinLeft,
                this.wheelSpinRight);
    }

    public PatriotPose withTraverse(double degrees) {
        return new PatriotPose(this.supports, this.lift, this.service, degrees, this.wheelSpinLeft,
                this.wheelSpinRight);
    }

    public PatriotPose withWheels(double left, double right) {
        return new PatriotPose(this.supports, this.lift, this.service, this.traverseDegrees, left, right);
    }

    public static TelHitboxPose.Part partOf(PatriotKind kind, int group, TelPose.Clip clip, double ageTicks) {
        String name = PatriotBones.clipName(kind, group, clip);
        return name == null ? TelHitboxPose.Part.REST
                : new TelHitboxPose.Part(name, TelPose.seconds(clip, ageTicks));
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
        if (bone.equals("traverse")) {
            return kind.isLauncher()
                    ? new TelAnimSampler.BoneValue(new double[] {0.0, this.traverseDegrees, 0.0}, null, null)
                    : null;
        }
        if (bone.startsWith("wheel_")) {
            double spin = isLeft(bone) ? this.wheelSpinLeft : this.wheelSpinRight;
            return new TelAnimSampler.BoneValue(new double[] {-spin, 0.0, 0.0}, null, null);
        }
        return null;
    }
}
