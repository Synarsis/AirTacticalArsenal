package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.entity.patriot.PatriotGeometry;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotHitbox;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotPose;
import com.synarsis.airtacticalarsenal.entity.patriot.TractorGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelEngine;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelEngineState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Wheel spin direction")
class WheelSpinTest {

    private static final double METRES = 0.8;

    @Test
    @DisplayName("a wrapped angle unwraps to the shortest step")
    void unwrap() {
        assertEquals(0.3, WheelSpin.delta(6.1, 6.1 + 0.3 - 2.0 * Math.PI), 1.0E-9);
        assertEquals(-0.3, WheelSpin.delta(0.1, 0.1 - 0.3 + 2.0 * Math.PI), 1.0E-9);
        assertEquals(1.0, WheelSpin.delta(2.0, 3.0), 1.0E-9);
        double turn = 0.0;
        double raw = 0.0;
        for (int i = 0; i < 100; i++) {
            double next = (raw + 1.1) % (2.0 * Math.PI);
            turn += WheelSpin.delta(raw, next);
            raw = next;
        }
        assertEquals(110.0, turn, 1.0E-6);
    }

    @Test
    @DisplayName("launcher, tractor and trailer wheels turn the same way going forward")
    void sameDirectionForward() {
        double telTurn = METRES / 0.531;
        double telBone = WheelSpin.boneRotation(telTurn);

        double tractorBone = WheelSpin.travelRotation(METRES, TractorGeometry.WHEEL_RADIUS);

        PatriotHitbox model = PatriotHitbox.of(PatriotKind.LAUNCHER);
        double trailerSpin = Math.toDegrees(WheelSpin.travelRotation(METRES, PatriotGeometry.TRAILER_WHEEL_RADIUS));
        double trailerBone = PatriotPose.REST.withWheels(trailerSpin, trailerSpin)
                .value(PatriotKind.LAUNCHER, model.sampler(), "wheel_left_0", -1).geckoRotation()[0];

        assertTrue(telBone < 0.0 && tractorBone < 0.0 && trailerBone < 0.0,
                telBone + " " + tractorBone + " " + trailerBone);
        assertEquals(WheelSpin.travelRotation(METRES, PatriotGeometry.TRAILER_WHEEL_RADIUS), trailerBone, 1.0E-9);
    }

    @Test
    @DisplayName("going backward every wheel turns the other way")
    void backward() {
        assertTrue(WheelSpin.travelRotation(-METRES, 0.5) > 0.0);
        assertTrue(WheelSpin.boneRotation(-1.0) > 0.0);
    }

    @Test
    @DisplayName("the launcher's built-in engine counts the turn the same way as Jolt does")
    void legacyEngineMatchesJolt() {
        TelEngineState state = new TelEngineState();
        TelEngine.advanceWheels(state, METRES, 1.0, 0.531);
        assertTrue(state.wheelRotL > 0.0 && state.wheelRotR > 0.0);
        assertTrue(WheelSpin.boneRotation(state.wheelRotL) < 0.0);
    }

    @Test
    @DisplayName("the Jolt wheel turn grows without wrapping and follows the travel")
    void joltTurn() {
        try (VehicleRig rig = new VehicleRig(VehicleRig.FLAT, TrainSpec.m983TractorDefaults(), 100.5, 100.5, 0.0)) {
            rig.run(60);
            double z0 = rig.vehicle.snapshot().z();
            double previous = rig.vehicle.wheelTurn(0);
            for (int i = 0; i < 100; i++) {
                rig.drive(1.0, 0.0, 0.0, 0.0);
                rig.run(1);
                double now = rig.vehicle.wheelTurn(0);
                assertTrue(now >= previous - 0.05, "the turn went back at tick " + i);
                previous = now;
            }
            double travel = rig.vehicle.snapshot().z() - z0;
            double expected = travel / TractorGeometry.WHEEL_RADIUS;
            assertTrue(Math.abs(previous - expected) < 0.25 * expected, previous + " against " + expected);
        }
    }
}
