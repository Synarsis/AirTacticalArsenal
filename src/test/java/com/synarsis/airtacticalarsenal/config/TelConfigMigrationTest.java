package com.synarsis.airtacticalarsenal.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Launcher config migration")
class TelConfigMigrationTest {

    @Test
    @DisplayName("a file from before the versioning, or from any earlier version, is migrated")
    void oldFilesAreMigrated() {
        for (int v = 0; v < TelConfigMigration.CURRENT_VERSION; v++) {
            assertTrue(TelConfigMigration.isNeeded(v), "version " + v);
            assertEquals(TelConfigMigration.PHYSICS_KEYS, TelConfigMigration.keysToReset(v));
        }
    }

    @Test
    @DisplayName("a file at the current version, or newer, is left exactly as the player has it")
    void currentFilesAreLeftAlone() {
        for (int v : new int[] {TelConfigMigration.CURRENT_VERSION, TelConfigMigration.CURRENT_VERSION + 1, 99}) {
            assertFalse(TelConfigMigration.isNeeded(v));
            assertTrue(TelConfigMigration.keysToReset(v).isEmpty());
        }
    }

    @Test
    @DisplayName("only the driving keys are reset, never health, the switches or the version itself")
    void listIsTheDrivingKeys() {
        assertTrue(TelConfigMigration.PHYSICS_KEYS.containsAll(List.of(
                "telMaxForwardSpeed", "telMaxBackwardSpeed", "telAcceleration", "telClimb")));
        assertFalse(TelConfigMigration.PHYSICS_KEYS.contains("telMaxHealth"));
        assertFalse(TelConfigMigration.PHYSICS_KEYS.contains("telBreakSoftBlocks"));
        assertFalse(TelConfigMigration.PHYSICS_KEYS.contains("telConfigVersion"));
    }

    @Test
    @DisplayName("every key the migration names is one the spec can reset, with a default to reset it to")
    void everyNamedKeyCanBeReset() {
        assertEquals(new java.util.TreeSet<>(TelConfigMigration.PHYSICS_KEYS),
                new java.util.TreeSet<>(ShahedConfig.TEL_PHYSICS_KEYS.keySet()));
        for (var value : ShahedConfig.TEL_PHYSICS_KEYS.values()) {
            assertTrue(value.getDefault() > 0.0);
        }
        assertEquals(TelConfigMigration.CURRENT_VERSION, ShahedConfig.TEL_CONFIG_VERSION.getDefault());
        assertEquals(0.018, ShahedConfig.TEL_ACCELERATION.getDefault(), 1.0E-12);
        assertEquals(0.95, ShahedConfig.TEL_MAX_FORWARD_SPEED.getDefault(), 1.0E-12);
        assertEquals(0.45, ShahedConfig.TEL_MAX_BACKWARD_SPEED.getDefault(), 1.0E-12);
    }
}
