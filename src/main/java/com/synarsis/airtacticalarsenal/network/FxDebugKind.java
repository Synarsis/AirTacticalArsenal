package com.synarsis.airtacticalarsenal.network;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum FxDebugKind {
    LAUNCH,
    IMPACT,
    SHAHED,
    WRECK,
    FLASH,
    LIGHTS_ON,
    LIGHTS_OFF,
    LIGHTS_BUFFER,
    LIGHTS_VOXELS,
    PHYSICS_ON,
    PHYSICS_OFF;

    private static final FxDebugKind[] VALUES = values();

    public static String[] sceneNames() {
        return Arrays.stream(VALUES).filter(FxDebugKind::isScene).map(FxDebugKind::commandName).toArray(String[]::new);
    }

    public boolean isScene() {
        return this != LIGHTS_ON && this != LIGHTS_OFF && this != LIGHTS_BUFFER && this != LIGHTS_VOXELS
                && this != PHYSICS_ON && this != PHYSICS_OFF;
    }

    public String commandName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FxDebugKind> scene(String name) {
        return Arrays.stream(VALUES).filter(FxDebugKind::isScene).filter(k -> k.commandName().equals(name.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static FxDebugKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FLASH;
    }
}
