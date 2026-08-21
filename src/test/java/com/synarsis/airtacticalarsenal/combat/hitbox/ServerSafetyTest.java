package com.synarsis.airtacticalarsenal.combat.hitbox;

import com.synarsis.airtacticalarsenal.debug.DebugOverrides;
import com.synarsis.airtacticalarsenal.repro.ClassRefs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These classes are edited from both sides, so it is easy to reach for a client type in one and
 * not notice until a dedicated server refuses to start.
 */
@DisplayName("Server safety: combat and debug classes stay off the client")
class ServerSafetyTest {

    private static final String CLIENT_PACKAGE = "net/minecraft/client";

    private static void assertServerSafe(Class<?> type) {
        assertFalse(ClassRefs.of(type).referencesAnythingIn(CLIENT_PACKAGE),
                type.getSimpleName() + " names a net.minecraft.client type; a dedicated server "
                        + "cannot load it, and whatever registers it will throw during mod setup");
    }

    @Test
    @DisplayName("the hit geometry never names a client class")
    void hitboxClassesAreServerSafe() {
        assertServerSafe(Obb.class);
        assertServerSafe(PartBox.class);
        assertServerSafe(HitPart.class);
        assertServerSafe(ObbProvider.class);
        assertServerSafe(ShahedHitboxes.class);
    }

    @Test
    @DisplayName("the outcome model and its effects never name a client class")
    void combatLogicIsServerSafe() {
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.outcome.FallMode.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.outcome.FallController.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.outcome.OutcomeResolver.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.outcome.FuzeState.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.outcome.ImpactOutcome.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.damage.PartHealth.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.effect.FallEffects.class);
        assertServerSafe(com.synarsis.airtacticalarsenal.combat.sweep.BulletSweep.class);
    }

    @Test
    @DisplayName("the Explosion Overhaul layer never names a client class")
    void explosionOverhaulLayerIsServerSafe() {
        // Explosions are resolved server-side; reaching for a client type here would stop a
        // dedicated server loading the mod at all.
        assertServerSafe(com.synarsis.airtacticalarsenal.compat.ExplosionOverhaulCompat.class);
    }

    @Test
    @DisplayName("the testing switches never name a client class")
    void debugOverridesAreServerSafe() {
        assertServerSafe(DebugOverrides.class);
    }

    @Test
    @DisplayName("control: a client class is detectable this way")
    void controlTheScanFindsClientReferences() {
        // Without this, every assertion above would also pass on a scanner that found nothing at all.
        assertTrue(ClassRefs.of(com.synarsis.airtacticalarsenal.client.debug.HitboxDebugRenderer.class)
                        .referencesAnythingIn(CLIENT_PACKAGE),
                "the wireframe renderer is client-only and must show up as such");
    }
}
