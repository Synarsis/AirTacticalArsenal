package com.synarsis.airtacticalarsenal.combat.outcome;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The warhead, and what it does on arrival")
class FuzeTest {

    private static final double DUD_CHANCE = 0.35;

    @Test
    @DisplayName("an untouched warhead is armed, a wrecked one is dead")
    void fuzeFollowsWarheadDurability() {
        assertEquals(FuzeState.ARMED, FuzeState.fromWarhead(18.0f, 18.0f));
        assertEquals(FuzeState.ARMED, FuzeState.fromWarhead(9.0f, 18.0f), "exactly half still counts");
        assertEquals(FuzeState.DEGRADED, FuzeState.fromWarhead(4.0f, 18.0f));
        assertEquals(FuzeState.DEAD, FuzeState.fromWarhead(0.0f, 18.0f));
    }

    @Test
    @DisplayName("a drone with no warhead section at all is treated as armed")
    void missingSectionDoesNotDisarmEverything() {
        assertEquals(FuzeState.ARMED, FuzeState.fromWarhead(0.0f, 0.0f));
    }

    @Test
    @DisplayName("an intact warhead goes off where it lands")
    void armedDetonates() {
        assertEquals(ImpactOutcome.FULL,
                ImpactOutcome.resolve(FuzeState.ARMED, 2.0, true, DUD_CHANCE, 0.99));
        assertEquals(ImpactOutcome.FULL,
                ImpactOutcome.resolve(FuzeState.ARMED, 0.1, true, DUD_CHANCE, 0.0),
                "even a gentle arrival: the fuze is fine");
    }

    @Test
    @DisplayName("a destroyed warhead never fires, however it arrives")
    void deadFuzeAlwaysDuds() {
        for (double speed : new double[]{0.0, 1.0, 3.2}) {
            for (double roll : new double[]{0.0, 0.5, 0.999}) {
                assertEquals(ImpactOutcome.DUD,
                        ImpactOutcome.resolve(FuzeState.DEAD, speed, true, DUD_CHANCE, roll));
            }
        }
    }

    @Test
    @DisplayName("a damaged warhead is likelier to fire when it arrives hard")
    void degradedDependsOnArrival() {
        assertEquals(ImpactOutcome.FULL,
                ImpactOutcome.resolve(FuzeState.DEGRADED, 3.0, true, DUD_CHANCE, 0.5));
        assertNotEquals(ImpactOutcome.FULL,
                ImpactOutcome.resolve(FuzeState.DEGRADED, 0.3, true, DUD_CHANCE, 0.5),
                "a drone that glided in gently should not go off as if it had dived");
    }

    @Test
    @DisplayName("a damaged warhead that fails leaves something behind")
    void degradedCanDud() {
        assertEquals(ImpactOutcome.DUD,
                ImpactOutcome.resolve(FuzeState.DEGRADED, 0.3, true, DUD_CHANCE, 0.0));
        assertEquals(ImpactOutcome.LOW_ORDER,
                ImpactOutcome.resolve(FuzeState.DEGRADED, 0.3, true, DUD_CHANCE, 0.99));
    }

    @Test
    @DisplayName("with the config switch off, nothing shot down ever detonates fully")
    void serversCanForbidFallenDetonations() {
        for (FuzeState fuze : FuzeState.values()) {
            for (double speed : new double[]{0.0, 3.2}) {
                assertNotEquals(ImpactOutcome.FULL,
                        ImpactOutcome.resolve(fuze, speed, false, DUD_CHANCE, 0.99),
                        fuze.id() + " detonated fully with fallen detonations turned off");
            }
        }
    }

    @Test
    @DisplayName("the dud chance actually governs how often a gentle arrival fails")
    void dudChanceIsHonoured() {
        int duds = 0;
        for (int i = 0; i < 1000; i++) {
            double roll = i / 1000.0;
            if (ImpactOutcome.resolve(FuzeState.DEGRADED, 0.3, true, DUD_CHANCE, roll) == ImpactOutcome.DUD) {
                duds++;
            }
        }
        assertEquals(DUD_CHANCE, duds / 1000.0, 0.01);
    }

    @Test
    @DisplayName("outcomes and fuze states keep stable ids")
    void idsAreStable() {
        assertEquals("armed", FuzeState.ARMED.id());
        assertEquals("degraded", FuzeState.DEGRADED.id());
        assertEquals("dead", FuzeState.DEAD.id());
        assertEquals("full", ImpactOutcome.FULL.id());
        assertEquals("low_order", ImpactOutcome.LOW_ORDER.id());
        assertEquals("dud", ImpactOutcome.DUD.id());
        for (FuzeState state : FuzeState.values()) {
            assertTrue(state.id().matches("[a-z_]+"), state.name());
        }
    }
}
