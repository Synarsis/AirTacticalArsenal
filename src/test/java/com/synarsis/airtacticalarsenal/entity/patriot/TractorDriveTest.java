package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Driving the tractor")
class TractorDriveTest {

    @Test
    @DisplayName("the keys become throttle, steering, handbrake and boost; the right key steers to the right")
    void keys() {
        JoltVehicle.Controls forward = TractorDrive.controlsFor(TelInput.FWD);
        assertEquals(1.0, forward.throttle(), 0.0);
        assertEquals(0.0, forward.steer(), 0.0);
        assertEquals(-1.0, TractorDrive.controlsFor(TelInput.BACK).throttle(), 0.0);
        assertEquals(0.0, TractorDrive.controlsFor(TelInput.FWD | TelInput.BACK).throttle(), 0.0);
        assertTrue(TractorDrive.controlsFor(TelInput.RIGHT).steer() < 0.0,
                "the vehicle model takes a negative number for a turn toward minus X, which is the right");
        assertTrue(TractorDrive.controlsFor(TelInput.LEFT).steer() > 0.0);
        assertEquals(0.0, TractorDrive.controlsFor(TelInput.LEFT | TelInput.RIGHT).steer(), 0.0);
        assertEquals(1.0, TractorDrive.controlsFor(TelInput.BRAKE).handbrake(), 0.0);
        assertTrue(TractorDrive.controlsFor(TelInput.BOOST).boost());
        assertFalse(TractorDrive.controlsFor(0).boost());
    }

    @Test
    @DisplayName("on its side or on its back counts as overturned")
    void overturned() {
        assertTrue(TractorDrive.overturned(0.0, 70.0));
        assertTrue(TractorDrive.overturned(-65.0, 0.0));
        assertFalse(TractorDrive.overturned(10.0, -20.0));
    }
}
