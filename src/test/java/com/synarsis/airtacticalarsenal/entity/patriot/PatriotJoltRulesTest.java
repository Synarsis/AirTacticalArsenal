package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.patriot.PatriotJolt.Role;
import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Who computes the Patriot")
class PatriotJoltRulesTest {

    @Test
    @DisplayName("the server follows a driver and computes alone, the driver's client computes, and held means static")
    void roles() {
        assertEquals(Role.FOLLOW, PatriotJolt.decide(false, true, true, false, false));
        assertEquals(Role.SIMULATE, PatriotJolt.decide(false, true, false, false, false));
        assertEquals(Role.HOLD, PatriotJolt.decide(false, true, false, true, false));
        assertEquals(Role.HOLD, PatriotJolt.decide(false, true, true, true, false));
        assertEquals(Role.SIMULATE, PatriotJolt.decide(true, true, true, false, false));
        assertEquals(Role.HOLD, PatriotJolt.decide(true, true, true, true, false));
        assertEquals(Role.FOLLOW, PatriotJolt.decide(true, false, false, false, true));
        assertEquals(Role.NONE, PatriotJolt.decide(true, false, false, false, false));
        assertEquals(Role.NONE, PatriotJolt.decide(true, false, false, true, false));
    }

    @Test
    @DisplayName("the keys become throttle, steering, handbrake and boost; the right key steers to the right")
    void keys() {
        JoltVehicle.Controls forward = PatriotJolt.controlsFor(TelInput.FWD);
        assertEquals(1.0, forward.throttle(), 0.0);
        assertEquals(0.0, forward.steer(), 0.0);
        assertEquals(-1.0, PatriotJolt.controlsFor(TelInput.BACK).throttle(), 0.0);
        assertEquals(0.0, PatriotJolt.controlsFor(TelInput.FWD | TelInput.BACK).throttle(), 0.0);
        assertTrue(PatriotJolt.controlsFor(TelInput.RIGHT).steer() < 0.0,
                "the vehicle model takes a negative number for a turn toward minus X, which is the right");
        assertTrue(PatriotJolt.controlsFor(TelInput.LEFT).steer() > 0.0);
        assertEquals(0.0, PatriotJolt.controlsFor(TelInput.LEFT | TelInput.RIGHT).steer(), 0.0);
        assertEquals(1.0, PatriotJolt.controlsFor(TelInput.BRAKE).handbrake(), 0.0);
        assertTrue(PatriotJolt.controlsFor(TelInput.BOOST).boost());
        assertFalse(PatriotJolt.controlsFor(0).boost());
    }

    @Test
    @DisplayName("on its side or on its back counts as overturned")
    void overturned() {
        assertTrue(PatriotJolt.overturned(0.0, 70.0));
        assertTrue(PatriotJolt.overturned(-65.0, 0.0));
        assertFalse(PatriotJolt.overturned(10.0, -20.0));
    }
}
