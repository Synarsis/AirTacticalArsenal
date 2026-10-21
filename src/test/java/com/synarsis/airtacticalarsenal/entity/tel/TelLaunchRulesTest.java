package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Aim;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Denial;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Refusal;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Stance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("When the launcher may be deployed and fired")
class TelLaunchRulesTest {

    private static final Stance STILL = new Stance(0.0, true, false, 1.0, -2.0);

    @Test
    @DisplayName("standing still on level ground is fine")
    void stanceFine() {
        assertEquals(Refusal.NONE, TelLaunchRules.stance(STILL, 8.0));
    }

    @Test
    @DisplayName("each way of not standing still has its own reason")
    void stanceReasons() {
        assertEquals(Refusal.MOVING, TelLaunchRules.stance(new Stance(0.05, true, false, 0, 0), 8.0));
        assertEquals(Refusal.AIRBORNE, TelLaunchRules.stance(new Stance(0, false, false, 0, 0), 8.0));
        assertEquals(Refusal.IN_WATER, TelLaunchRules.stance(new Stance(0, true, true, 0, 0), 8.0));
        assertEquals(Refusal.TILT, TelLaunchRules.stance(new Stance(0, true, false, 9.0, 0), 8.0));
        assertEquals(Refusal.TILT, TelLaunchRules.stance(new Stance(0, true, false, 0, -8.5), 8.0));
        assertEquals(Refusal.NONE, TelLaunchRules.stance(new Stance(0, true, false, -8.0, 8.0), 8.0));
    }

    @Test
    @DisplayName("a target is set, far enough, near enough and allowed, in that order")
    void aimChecks() {
        assertEquals(Refusal.NONE, TelLaunchRules.aim(new Aim(true, 2000.0, 1000, 5000, false, false)));
        assertEquals(Refusal.NO_TARGET, TelLaunchRules.aim(new Aim(false, 0.0, 1000, 5000, false, false)));
        assertEquals(Refusal.TOO_CLOSE, TelLaunchRules.aim(new Aim(true, 500.0, 1000, 5000, false, false)));
        assertEquals(Refusal.TOO_FAR, TelLaunchRules.aim(new Aim(true, 6000.0, 1000, 5000, false, false)));
        assertEquals(Refusal.FORBIDDEN, TelLaunchRules.aim(new Aim(true, 2000.0, 1000, 5000, false, true)));
        assertEquals(Refusal.NONE, TelLaunchRules.aim(new Aim(true, 1000.0, 1000, 5000, false, false)),
                "the limits themselves are in range");
        assertEquals(Refusal.NONE, TelLaunchRules.aim(new Aim(true, 5000.0, 1000, 5000, false, false)));
    }

    @Test
    @DisplayName("with the limits switched off only the target and the zones count")
    void limitsOff() {
        assertEquals(Refusal.NONE, TelLaunchRules.aim(new Aim(true, 10.0, 1000, 5000, true, false)));
        assertEquals(Refusal.NONE, TelLaunchRules.aim(new Aim(true, 90000.0, 1000, 5000, true, false)));
        assertEquals(Refusal.NO_TARGET, TelLaunchRules.aim(new Aim(false, 0.0, 1000, 5000, true, false)));
        assertEquals(Refusal.FORBIDDEN, TelLaunchRules.aim(new Aim(true, 10.0, 1000, 5000, true, true)));
    }

    @Test
    @DisplayName("a denial carries the round number and the limit that was passed")
    void denialArguments() {
        Denial close = TelLaunchRules.aim(new Aim(true, 500.0, 1000, 5000, false, false), 1);
        assertEquals(Refusal.TOO_CLOSE, close.reason());
        assertArrayEquals(new Object[] {2, 1000}, close.args(), "round 2, minimum 1000");
        Denial far = TelLaunchRules.aim(new Aim(true, 9000.0, 1000, 5000, false, false), 0);
        assertArrayEquals(new Object[] {1, 5000}, far.args());
        Denial none = TelLaunchRules.aim(new Aim(false, 0.0, 1000, 5000, false, false), 0);
        assertArrayEquals(new Object[] {1}, none.args());
        assertTrue(TelLaunchRules.aim(new Aim(true, 2000.0, 1000, 5000, false, false), 0).ok());

        Denial sky = new Denial(Refusal.SKY_BLOCKED, 1, "12 64 -30", 0);
        assertArrayEquals(new Object[] {2, "12 64 -30"}, sky.args());
        Denial covers = new Denial(Refusal.COVERS_BLOCKED, -1, "3 70 4", 0);
        assertArrayEquals(new Object[] {"3 70 4"}, covers.args());
        assertEquals(0, Denial.of(Refusal.BUSY).args().length);
        assertFalse(Denial.of(Refusal.BUSY).ok());
        assertTrue(Denial.of(Refusal.NONE).ok());
    }

    @Test
    @DisplayName("every reason has a message key made from its name")
    void messageKeys() {
        assertEquals("message.ata.tel.sky_blocked", Refusal.SKY_BLOCKED.messageKey());
        assertEquals("message.ata.tel.covers_closed", Refusal.COVERS_CLOSED.messageKey());
        assertEquals("message.ata.tel.no_round_in_inventory", Refusal.NO_ROUND_IN_INVENTORY.messageKey());
    }

    @Test
    @DisplayName("distance is measured across the ground")
    void distance() {
        assertEquals(Math.hypot(2.0, 3.0), TelLaunchRules.groundDistance(10.0, 20.0, 12, 23), 1.0e-9);
        assertEquals(5.0, TelLaunchRules.groundDistance(0.0, 0.0, 3, 4), 1.0e-9);
    }
}
