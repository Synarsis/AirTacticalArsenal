package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Who calculates the launcher and what the keys mean")
class TelJoltRulesTest {

    @Test
    @DisplayName("the driver's client simulates, the server follows the driver and simulates an empty launcher")
    void roles() {
        assertEquals(TelJolt.Role.SIMULATE, TelJolt.decide(true, true, true, false, false));
        assertEquals(TelJolt.Role.HOLD, TelJolt.decide(true, true, true, true, false));
        assertEquals(TelJolt.Role.NONE, TelJolt.decide(true, false, false, false, false));
        assertEquals(TelJolt.Role.FOLLOW, TelJolt.decide(true, false, true, false, true));
        assertEquals(TelJolt.Role.FOLLOW, TelJolt.decide(false, true, true, false, false));
        assertEquals(TelJolt.Role.SIMULATE, TelJolt.decide(false, true, false, false, false));
        assertEquals(TelJolt.Role.HOLD, TelJolt.decide(false, true, false, true, false));
    }

    @Test
    @DisplayName("keys become throttle, steering, handbrake and boost; opposite keys cancel")
    void keys() {
        JoltVehicle.Controls forward = TelJolt.controlsFor(TelInput.FWD, true);
        assertEquals(1.0, forward.throttle(), 0.0);
        assertEquals(0.0, forward.steer(), 0.0);
        assertEquals(-1.0, TelJolt.controlsFor(TelInput.BACK, true).throttle(), 0.0);
        assertEquals(0.0, TelJolt.controlsFor(TelInput.FWD | TelInput.BACK, true).throttle(), 0.0);
        assertEquals(1.0, TelJolt.controlsFor(TelInput.RIGHT, true).steer(), 0.0);
        assertEquals(-1.0, TelJolt.controlsFor(TelInput.LEFT, true).steer(), 0.0);
        assertEquals(0.0, TelJolt.controlsFor(TelInput.LEFT | TelInput.RIGHT, true).steer(), 0.0);
        assertEquals(1.0, TelJolt.controlsFor(TelInput.BRAKE, true).handbrake(), 0.0);
        assertTrue(TelJolt.controlsFor(TelInput.BOOST, true).boost());
        assertFalse(TelJolt.controlsFor(0, true).boost());
    }

    @Test
    @DisplayName("with the jacks out the keys are ignored and the launcher is braked")
    void jacksOut() {
        JoltVehicle.Controls braked = TelJolt.controlsFor(TelInput.FWD | TelInput.RIGHT, false);
        assertEquals(0.0, braked.throttle(), 0.0);
        assertEquals(0.0, braked.steer(), 0.0);
        assertEquals(1.0, braked.brake(), 0.0);
        assertEquals(1.0, braked.handbrake(), 0.0);
    }

    @Test
    @DisplayName("on its side or its roof counts as overturned")
    void overturned() {
        assertFalse(TelJolt.overturned(0.0, 0.0));
        assertFalse(TelJolt.overturned(20.0, -35.0));
        assertTrue(TelJolt.overturned(0.0, 75.0));
        assertTrue(TelJolt.overturned(0.0, -170.0));
        assertTrue(TelJolt.overturned(-80.0, 0.0));
    }
}
