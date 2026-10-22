package com.synarsis.airtacticalarsenal.vehicle.lamp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Rear lamps: dim with the headlights, bright when braking")
class BrakeLightsTest {

    @Test
    @DisplayName("a driven vehicle at a steady speed shows the dim lamps with the headlights and nothing without them")
    void steady() {
        BrakeLights lights = new BrakeLights();
        for (int i = 0; i < 20; i++) {
            lights.update(8.0, true);
        }
        assertFalse(lights.braking());
        assertEquals(BrakeLights.DIM, lights.rearBrightness(true), 0.0);
        assertEquals(0.0, lights.rearBrightness(false), 0.0);
        assertEquals(1.0, BrakeLights.frontBrightness(true), 0.0);
        assertEquals(0.0, BrakeLights.frontBrightness(false), 0.0);
    }

    @Test
    @DisplayName("slowing down lights the lamps at full strength and holds them a few ticks")
    void braking() {
        BrakeLights lights = new BrakeLights();
        lights.update(10.0, true);
        lights.update(10.0, true);
        lights.update(8.0, true);
        assertTrue(lights.braking());
        assertEquals(1.0, lights.rearBrightness(false), 0.0, "braking lights burn even with the headlights off");
        for (int i = 0; i < BrakeLights.HOLD_TICKS - 1; i++) {
            lights.update(8.0, true);
            assertTrue(lights.braking(), "held, tick " + i);
        }
        lights.update(8.0, true);
        assertFalse(lights.braking());
    }

    @Test
    @DisplayName("braking while reversing counts too, and speeding up does not")
    void reversingAndAccelerating() {
        BrakeLights reverse = new BrakeLights();
        reverse.update(-4.0, true);
        reverse.update(-2.0, true);
        assertTrue(reverse.braking());
        BrakeLights faster = new BrakeLights();
        faster.update(2.0, true);
        faster.update(6.0, true);
        assertFalse(faster.braking());
        BrakeLights turnAround = new BrakeLights();
        turnAround.update(3.0, true);
        turnAround.update(-1.0, true);
        assertTrue(turnAround.braking(), "a sudden swing from forward to reverse is a stop");
    }

    @Test
    @DisplayName("a parked vehicle with nobody aboard holds its brake lamps lit, a creeping driven one does not")
    void parked() {
        BrakeLights lights = new BrakeLights();
        lights.update(0.0, false);
        assertTrue(lights.braking());
        BrakeLights driven = new BrakeLights();
        driven.update(0.0, true);
        driven.update(0.0, true);
        assertFalse(driven.braking());
        BrakeLights moving = new BrakeLights();
        moving.update(5.0, false);
        moving.update(5.0, false);
        assertFalse(moving.braking());
    }
}
