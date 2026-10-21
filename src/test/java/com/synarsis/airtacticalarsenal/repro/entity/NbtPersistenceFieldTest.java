package com.synarsis.airtacticalarsenal.repro.entity;

import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.repro.ClassRefs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("F-2/F-8: flight-critical fields are missing from entity NBT")
class NbtPersistenceFieldTest {

    private static final String LEGIT_SPAWN_KEY = "LegitSpawn";

    @Test
    void controlOrlanDoesPersistItsLegitSpawnFlag() {
        assertTrue(ClassRefs.of(OrlanEntity.class).references(LEGIT_SPAWN_KEY),
                "OrlanEntity was expected to persist " + LEGIT_SPAWN_KEY
                        + " -- if this control fails the test is wrong, not the mod.");
    }

    @Test
    void shahedMustPersistItsLegitSpawnFlag() {
        assertTrue(ClassRefs.of(ShahedEntity.class).references(LEGIT_SPAWN_KEY),
                "ShahedEntity never writes " + LEGIT_SPAWN_KEY + " to NBT, so a drone reloaded"
                        + " from disk comes back with isLegitSpawn=false and discards itself on its"
                        + " first tick. Because remove(DISCARDED) outside WARMUP is treated as a"
                        + " shoot-down, every drone in the air at restart also broadcasts a full"
                        + " explosion to every player. OrlanEntity persists the same flag.");
    }

    @Test
    void iskanderMustPersistItsLegitSpawnFlag() {
        assertTrue(ClassRefs.of(IskanderEntity.class).references(LEGIT_SPAWN_KEY),
                "IskanderEntity never writes " + LEGIT_SPAWN_KEY + " to NBT: every missile in"
                        + " flight is deleted on server restart.");
    }

    @Test
    void iskanderMustPersistTheLauncherItLaunchedFrom() {
        ClassRefs refs = ClassRefs.of(IskanderEntity.class);
        assertTrue(refs.references("LauncherVehicle"),
                "IskanderEntity.addAdditionalSaveData writes the full trajectory but not the"
                        + " launcher vehicle it left. canHitEntity uses that id to keep the missile from"
                        + " exploding on its own launcher (or its hit parts) on the way up, so after a"
                        + " restart mid-climb the missile would blow up on the machine it was fired from.");
    }
}
