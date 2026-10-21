package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.client.model.IskanderTelModel;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotJolt;
import com.synarsis.airtacticalarsenal.entity.tel.TelDriving;
import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelEngine;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelEngineState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Which way the vehicles steer, and what the yaw assist does")
class JoltSteeringTest {

    private static final double START_X = 100.5;
    private static final double START_Z = 100.5;

    private static void say(String line) {
        VehicleRig.report(line);
    }

    private static void driveKeys(VehicleRig rig, int keys, int ticks) {
        for (int i = 0; i < ticks; i++) {
            rig.vehicle.setControls(TelDriving.controls(keys));
            rig.run(1);
        }
    }

    @Test
    @DisplayName("the launcher: the right key turns right (the yaw grows, x falls), the left key the other way")
    void launcherKeys() {
        try (VehicleRig rig = new VehicleRig(VehicleRig.FLAT, START_X, START_Z, 0.0)) {
            rig.run(60);
            JoltVehicle.Snapshot start = rig.vehicle.snapshot();
            driveKeys(rig, TelInput.FWD | TelInput.RIGHT, 60);
            JoltVehicle.Snapshot right = rig.vehicle.snapshot();
            say(String.format(Locale.ROOT, "RESULT TEL right key 3 s: yaw %.1f, x %.2f", right.yawDeg(), right.x() - start.x()));
            assertTrue(PoseMath.wrapDegrees(right.yawDeg() - start.yawDeg()) > 10.0, "yaw " + right.yawDeg());
            assertTrue(right.x() < start.x() - 0.5, "x " + right.x());
        }
        try (VehicleRig rig = new VehicleRig(VehicleRig.FLAT, START_X, START_Z, 0.0)) {
            rig.run(60);
            JoltVehicle.Snapshot start = rig.vehicle.snapshot();
            driveKeys(rig, TelInput.FWD | TelInput.LEFT, 60);
            JoltVehicle.Snapshot left = rig.vehicle.snapshot();
            assertTrue(PoseMath.wrapDegrees(left.yawDeg() - start.yawDeg()) < -10.0, "yaw " + left.yawDeg());
            assertTrue(left.x() > start.x() + 0.5, "x " + left.x());
        }
    }

    @Test
    @DisplayName("the Patriot train: the same keys turn it the same way")
    void trainKeys() {
        for (boolean right : new boolean[] {true, false}) {
            try (TrainRig rig = new TrainRig(TrainRig.FLAT, TrainSpec.launcherDefaults(), START_X, START_Z, 0.0)) {
                rig.run(60);
                JoltVehicle.Snapshot start = rig.train.tractor().snapshot();
                int keys = TelInput.FWD | (right ? TelInput.RIGHT : TelInput.LEFT);
                for (int i = 0; i < 60; i++) {
                    rig.train.tractor().setControls(PatriotJolt.controlsFor(keys));
                    rig.run(1);
                }
                JoltVehicle.Snapshot end = rig.train.tractor().snapshot();
                double turned = PoseMath.wrapDegrees(end.yawDeg() - start.yawDeg());
                say(String.format(Locale.ROOT, "RESULT train %s key 3 s: yaw %.1f, x %.2f", right ? "right" : "left", turned,
                        end.x() - start.x()));
                if (right) {
                    assertTrue(turned > 10.0 && end.x() < start.x() - 0.5, "right: yaw " + turned + " x " + end.x());
                } else {
                    assertTrue(turned < -10.0 && end.x() > start.x() + 0.5, "left: yaw " + turned + " x " + end.x());
                }
            }
        }
    }

    @Test
    @DisplayName("the front wheels of the model turn the same way for the Jolt launcher and the built-in one")
    void wheelsAgreeWithLegacy() {
        TelEngineState legacy = new TelEngineState();
        TelEngine.Context context = new TelEngine.Context(0.0, 0.0, 0.5, 0.0, 0.0, 1.0, true, false, 0.0, 0.0, true,
                TelEngine.DEFAULTS);
        for (int i = 0; i < 15; i++) {
            TelEngine.step(legacy, TelInput.FWD | TelInput.RIGHT, context);
        }
        double legacySteer = legacy.steerDegrees();
        assertTrue(legacySteer > 5.0, "built-in physics, right: " + legacySteer);

        try (VehicleRig rig = new VehicleRig(VehicleRig.FLAT, START_X, START_Z, 0.0)) {
            rig.run(60);
            driveKeys(rig, TelInput.FWD | TelInput.RIGHT, 30);
            int front = (rig.vehicle.wheelCount() / 2 - 1) * 2;
            double joltSteer = -Math.toDegrees(rig.vehicle.wheelSteer(front));
            assertTrue(joltSteer > 5.0, "Jolt, right: " + joltSteer);
            assertEquals(Math.signum(legacySteer), Math.signum(joltSteer));
        }
        assertTrue(IskanderTelModel.steerRotationY(10.0) < 0.0,
                "a positive steer is a turn about the up axis in the negative direction, which points the wheels to minus X");
        assertTrue(IskanderTelModel.steerRotationY(-10.0) > 0.0);
    }

    private static double steadyYawRate(VehicleSpec spec, double steer) {
        try (VehicleRig rig = new VehicleRig(VehicleRig.FLAT, spec, START_X, START_Z, 0.0)) {
            rig.run(60);
            double sum = 0.0;
            int n = 0;
            for (int i = 0; i < 20 * 8; i++) {
                rig.drive(Math.max(-0.3, Math.min(1.0, (40.0 - rig.forwardKmh()) * 0.3)), steer, 0.0, 0.0);
                rig.run(1);
                if (i >= 20 * 6) {
                    sum += Math.abs(rig.vehicle.snapshot().wy());
                    n++;
                }
            }
            return sum / n;
        }
    }

    @Test
    @DisplayName("the yaw assist helps a turn: at 40 km/h on full lock the yaw rate is no lower with it than without")
    void assistHelps() {
        for (VehicleSpec base : new VehicleSpec[] {VehicleSpec.defaults(), TrainSpec.tractorDefaults()}) {
            for (double steer : new double[] {1.0, -1.0}) {
                VehicleSpec with = VehicleSpec.parse(base.toJson());
                VehicleSpec without = VehicleSpec.parse(base.toJson());
                without.assist.yawStabilisation = 0.0;
                double assisted = steadyYawRate(with, steer);
                double plain = steadyYawRate(without, steer);
                say(String.format(Locale.ROOT, "RESULT yaw assist %s steer %+.0f at 40 km/h: with %.4f rad/s, without %.4f rad/s",
                        base.name, steer, assisted, plain));
                assertTrue(assisted >= plain - 0.002, base.name + " assisted " + assisted + " without " + plain);
            }
        }
    }
}
