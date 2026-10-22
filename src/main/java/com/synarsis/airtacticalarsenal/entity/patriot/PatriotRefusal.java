package com.synarsis.airtacticalarsenal.entity.patriot;

import java.util.Locale;

public enum PatriotRefusal {
    NONE,
    NOT_CREW,
    MOVING,
    BUSY,
    WRONG_VEHICLE,
    NEED_SUPPORTS,
    NEED_COUPLED,
    NEED_LIFT_UP,
    LIFT_NOT_DOWN,
    SERVICE_OPEN,
    ALREADY,
    TRAVERSE_LIMIT,
    SUPPORTS_NO_GROUND,
    LIFT_BLOCKED,
    TILT,
    AIRBORNE,
    IN_WATER,
    NO_PHYSICS;

    public boolean ok() {
        return this == NONE;
    }

    public String messageKey() {
        return "message.ata.patriot." + this.name().toLowerCase(Locale.ROOT);
    }

    public record Denial(PatriotRefusal reason, Object... args) {

        public static final Denial NONE = new Denial(PatriotRefusal.NONE);

        public boolean ok() {
            return this.reason == PatriotRefusal.NONE;
        }

        public static Denial of(PatriotRefusal reason) {
            return reason == PatriotRefusal.NONE ? NONE : new Denial(reason);
        }
    }
}
