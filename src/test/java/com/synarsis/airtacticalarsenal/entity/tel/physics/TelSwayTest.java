package com.synarsis.airtacticalarsenal.entity.tel.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Body sway")
class TelSwayTest {

    private static final double RATE = 0.6;

    @Test
    @DisplayName("speeding up lifts the nose, braking dips it")
    void pitchSigns() {
        TelSway up = new TelSway();
        TelSway down = new TelSway();
        for (int i = 0; i < 60; i++) {
            up.tick(0.01, 0.0, RATE);
            down.tick(-0.01, 0.0, RATE);
        }
        assertTrue(up.fakePitch < 0.0, "accelerating: " + up.fakePitch);
        assertTrue(down.fakePitch > 0.0, "braking: " + down.fakePitch);
    }

    @Test
    @DisplayName("in a right-hand turn the left side drops")
    void rollSign() {
        TelSway sway = new TelSway();
        for (int i = 0; i < 60; i++) {
            sway.tick(0.0, 0.01, RATE);
        }
        assertTrue(sway.fakeRoll < 0.0, "roll " + sway.fakeRoll);
    }

    @Test
    @DisplayName("a steady acceleration settles at a fixed lean, five times the spring's")
    void settles() {
        TelSway sway = new TelSway();
        for (int i = 0; i < 400; i++) {
            sway.tick(0.01, 0.0, RATE);
        }
        assertEquals(-0.15 * RATE / 0.2, sway.fakePitch, 0.01);
    }

    @Test
    @DisplayName("the full 0.02 gives about a degree")
    void aboutADegree() {
        TelSway sway = new TelSway();
        for (int i = 0; i < 400; i++) {
            sway.tick(10.0, 0.0, RATE);
        }
        assertEquals(-15.0 * TelSway.MAX_ACCELERATION * RATE / 0.2, sway.fakePitch, 0.01);
        assertTrue(Math.abs(sway.fakePitch) > 0.8 && Math.abs(sway.fakePitch) < 1.2);
    }

    @Test
    @DisplayName("it rocks back to level when the acceleration stops")
    void returnsToRest() {
        TelSway sway = new TelSway();
        for (int i = 0; i < 100; i++) {
            sway.tick(0.02, 0.02, RATE);
        }
        for (int i = 0; i < 400; i++) {
            sway.tick(0.0, 0.0, RATE);
        }
        assertEquals(0.0, sway.fakePitch, 1.0E-3);
        assertEquals(0.0, sway.fakeRoll, 1.0E-3);
    }

    @Test
    @DisplayName("a rate of zero turns it off")
    void rateZero() {
        TelSway sway = new TelSway();
        for (int i = 0; i < 100; i++) {
            sway.tick(0.02, 0.02, 0.0);
        }
        assertEquals(0.0, sway.fakePitch, 0.0);
    }
}
