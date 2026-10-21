package com.synarsis.airtacticalarsenal.entity.tel;

public enum TelState {
    STOWED(-1),
    COVERS_OPENING(TelPose.COVER_TICKS),
    COVERS_OPEN(-1),
    COVERS_CLOSING(TelPose.COVER_TICKS),
    COVER_LOAD(160),
    JACKS_DOWN(30),
    JACKS_UP(30),
    DEPLOYING(TelPose.COVER_TICKS + TelPose.LIFT_TICKS),
    RAISING(TelPose.LIFT_TICKS),
    RAISED(-1),
    COUNTDOWN(-1),
    LOWERING(TelPose.LIFT_TICKS),
    STOWING(TelPose.LIFT_TICKS + TelPose.COVER_TICKS);

    private final int duration;

    TelState(int duration) {
        this.duration = duration;
    }

    public int duration() {
        return this.duration;
    }

    public boolean isTransition() {
        return this.duration > 0;
    }

    public boolean isLoading() {
        return this == COVER_LOAD;
    }

    public boolean isUpright() {
        return this == RAISED || this == COUNTDOWN;
    }

    private static final TelState[] VALUES = values();

    public static TelState byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : STOWED;
    }
}
