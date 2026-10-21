package com.synarsis.airtacticalarsenal.config;

import java.util.List;

public final class TelConfigMigration {

    public static final int CURRENT_VERSION = 7;

    public static final List<String> PHYSICS_KEYS = List.of(
            "telMaxForwardSpeed", "telMaxBackwardSpeed", "telAcceleration", "telClimb",
            "telBrakePower", "telBrakeDamp", "telInertiaRotateRate");

    private TelConfigMigration() {
    }

    public static boolean isNeeded(int storedVersion) {
        return storedVersion < CURRENT_VERSION;
    }

    public static List<String> keysToReset(int storedVersion) {
        return isNeeded(storedVersion) ? PHYSICS_KEYS : List.of();
    }
}
