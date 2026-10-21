package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelBoneGroups;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;

public final class TelPose {

    public static final String PREFIX = "animation.iskander_tel_loaded.";
    public static final double HELD_SECONDS = 1000.0;

    public static final int COVER_TICKS = 56;
    public static final int LIFT_TICKS = 96;

    public enum Clip {
        REST,
        HELD,
        OPENING,
        CLOSING;

        private static final Clip[] VALUES = values();

        public static Clip byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : REST;
        }

        public boolean moving() {
            return this == OPENING || this == CLOSING;
        }

        public int endLevel() {
            return this == REST || this == CLOSING ? 0 : 1;
        }

        public int startLevel() {
            return this == REST || this == OPENING ? 0 : 1;
        }
    }

    private TelPose() {
    }

    public static int ticks(int group) {
        return group == TelBoneGroups.COVERS ? COVER_TICKS : LIFT_TICKS;
    }

    public static String clipName(int group, Clip clip) {
        if (clip == Clip.REST) {
            return null;
        }
        boolean opening = clip == Clip.OPENING || clip == Clip.HELD;
        return switch (group) {
            case TelBoneGroups.COVERS -> opening ? "cover_open" : "cover_close";
            case TelBoneGroups.LIFT_1 -> clip == Clip.CLOSING ? "lower_left" : "raise_left";
            default -> clip == Clip.CLOSING ? "lower_right" : "raise_right";
        };
    }

    public static double seconds(Clip clip, double ageTicks) {
        return clip.moving() ? Math.max(0.0, ageTicks) / 20.0 : HELD_SECONDS;
    }

    public static TelHitboxPose.Part part(int group, Clip clip, double ageTicks) {
        String name = clipName(group, clip);
        return name == null ? TelHitboxPose.Part.REST
                : new TelHitboxPose.Part(PREFIX + name, seconds(clip, ageTicks));
    }

    public static TelHitboxPose pose(Clip covers, double coversAge, Clip lift1, double lift1Age, Clip lift2,
            double lift2Age, double jackProgress, boolean loaded0, boolean loaded1) {
        return new TelHitboxPose(part(TelBoneGroups.COVERS, covers, coversAge), part(TelBoneGroups.LIFT_1, lift1, lift1Age),
                part(TelBoneGroups.LIFT_2, lift2, lift2Age), jackProgress, loaded0, loaded1);
    }
}
