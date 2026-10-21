package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

public record TelHitboxPose(Part covers, Part lift1, Part lift2, double jackProgress, boolean loaded0,
        boolean loaded1) {

    public record Part(String clip, double seconds) {

        public static final Part REST = new Part(null, 0.0);
    }

    public static final TelHitboxPose PARKED = new TelHitboxPose(Part.REST, Part.REST, Part.REST, 0.0, true, true);

    public static TelHitboxPose single(String clip, double seconds, double jackProgress, boolean loaded0,
            boolean loaded1) {
        Part p = new Part(clip, seconds);
        return new TelHitboxPose(p, p, p, jackProgress, loaded0, loaded1);
    }

    public Part part(int group) {
        return switch (group) {
            case TelBoneGroups.COVERS -> this.covers;
            case TelBoneGroups.LIFT_1 -> this.lift1;
            default -> this.lift2;
        };
    }

    public TelHitboxPose withLoaded(boolean first, boolean second) {
        return new TelHitboxPose(this.covers, this.lift1, this.lift2, this.jackProgress, first, second);
    }
}
