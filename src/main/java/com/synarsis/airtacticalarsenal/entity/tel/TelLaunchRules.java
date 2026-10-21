package com.synarsis.airtacticalarsenal.entity.tel;

import java.util.Locale;

public final class TelLaunchRules {

    public static final double STANDSTILL_SPEED = 0.02;

    public enum Refusal {
        NONE,
        NOT_CREW,
        BUSY,
        MOVING,
        AIRBORNE,
        IN_WATER,
        TILT,
        NO_MISSILE,
        NO_ROUND_IN_INVENTORY,
        SLOT_FULL,
        SLOT_EMPTY,
        NOT_RAISED,
        NO_TARGET,
        TOO_CLOSE,
        TOO_FAR,
        FORBIDDEN,
        SKY_BLOCKED,
        COVERS_BLOCKED,
        ERECTOR_BLOCKED,
        COVERS_CLOSED,
        ALREADY_RAISED;

        public boolean ok() {
            return this == NONE;
        }

        public String messageKey() {
            return "message.ata.tel." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Denial(Refusal reason, int slot, String where, int bound) {

        public static final Denial NONE = new Denial(Refusal.NONE, -1, "", 0);

        public static Denial of(Refusal reason) {
            return reason.ok() ? NONE : new Denial(reason, -1, "", 0);
        }

        public static Denial of(Refusal reason, int slot) {
            return new Denial(reason, slot, "", 0);
        }

        public boolean ok() {
            return this.reason.ok();
        }

        public Object[] args() {
            return switch (this.reason) {
                case NO_TARGET, FORBIDDEN -> new Object[] {this.slot + 1};
                case TOO_CLOSE, TOO_FAR -> new Object[] {this.slot + 1, this.bound};
                case SKY_BLOCKED, ERECTOR_BLOCKED -> new Object[] {this.slot + 1, this.where};
                case COVERS_BLOCKED -> new Object[] {this.where};
                default -> new Object[0];
            };
        }
    }

    public record Stance(double speed, boolean onGround, boolean inWater, double pitchDeg, double rollDeg) {
    }

    public record Aim(boolean hasTarget, double distance, int minRange, int maxRange, boolean limitsOff,
            boolean forbidden) {
    }

    private TelLaunchRules() {
    }

    public static Refusal stance(Stance s, double maxTilt) {
        if (s.speed() >= STANDSTILL_SPEED) {
            return Refusal.MOVING;
        }
        if (!s.onGround()) {
            return Refusal.AIRBORNE;
        }
        if (s.inWater()) {
            return Refusal.IN_WATER;
        }
        if (Math.abs(s.pitchDeg()) > maxTilt || Math.abs(s.rollDeg()) > maxTilt) {
            return Refusal.TILT;
        }
        return Refusal.NONE;
    }

    public static Refusal aim(Aim aim) {
        if (!aim.hasTarget()) {
            return Refusal.NO_TARGET;
        }
        if (!aim.limitsOff()) {
            if (aim.distance() < aim.minRange()) {
                return Refusal.TOO_CLOSE;
            }
            if (aim.distance() > aim.maxRange()) {
                return Refusal.TOO_FAR;
            }
        }
        return aim.forbidden() ? Refusal.FORBIDDEN : Refusal.NONE;
    }

    public static Denial aim(Aim aim, int slot) {
        Refusal r = aim(aim);
        if (r.ok()) {
            return Denial.NONE;
        }
        int bound = r == Refusal.TOO_CLOSE ? aim.minRange() : aim.maxRange();
        return new Denial(r, slot, "", bound);
    }

    public static double groundDistance(double fromX, double fromZ, int targetX, int targetZ) {
        return Math.hypot(targetX - fromX, targetZ - fromZ);
    }
}
