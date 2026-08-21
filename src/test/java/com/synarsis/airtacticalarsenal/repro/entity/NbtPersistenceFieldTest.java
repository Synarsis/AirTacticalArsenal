package com.synarsis.airtacticalarsenal.repro.entity;

import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.repro.ClassRefs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REPRO: entities F-2 and F-8 / blocks F-3 — state that decides whether an entity survives a
 * reload is never written to NBT.
 *
 * <p>Both flying entities gate their whole tick on a plain Java field:
 * {@code if (!level().isClientSide && !this.isLegitSpawn) { this.discard(); return; }}. The field
 * defaults to {@code false} and is set only by the code that spawns the entity legitimately.
 * Nothing writes it to NBT, so an entity restored from disk comes back with {@code false} and
 * deletes itself on its first tick.
 *
 * <p>The deletion is not quiet. {@code ShahedEntity.remove} treats a {@code DISCARDED} removal
 * outside the WARMUP phase as a shoot-down and fires the full explosion effect broadcast, so a
 * server restart with drones in the air greets every player with phantom detonations.
 *
 * <p>{@code IskanderEntity.launcherPos} has the same gap with a worse consequence: it is the only
 * handle back to the launcher block, and {@code releaseLauncher()} is what clears LOADED /
 * LAUNCHING / inFlight. Lose it across a restart and the launcher stays armed forever -- it can
 * no longer be loaded, fired, or unloaded.
 *
 * <p>An NBT key is a string constant in the class's constant pool, so its absence is provable
 * without a world. {@link OrlanEntity} serves as the control: it does persist {@code LegitSpawn},
 * which shows the pattern was understood and simply not applied to the other two entities.
 */
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
        boolean persisted = refs.references("LauncherX")
                || refs.references("LauncherPos")
                || refs.references("LauncherPosX");

        assertTrue(persisted,
                "IskanderEntity.addAdditionalSaveData writes the full trajectory but not"
                        + " launcherPos. releaseLauncher() is the only code that clears the"
                        + " launcher's LOADED/LAUNCHING/inFlight state and it needs that field, so"
                        + " after a restart mid-flight the launcher stays armed permanently:"
                        + " LoadingHatchBlock refuses to reload it, LaunchRocketPacket refuses to"
                        + " fire it, and RemoveRocketPacket refuses to unload it.");
    }
}
