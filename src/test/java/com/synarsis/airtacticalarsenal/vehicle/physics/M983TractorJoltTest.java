package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The M983 tractor alone on the real Jolt")
class M983TractorJoltTest {

    private static final double START_X = 100.5;
    private static final double START_Z = 100.5;

    private static void say(String line) {
        VehicleRig.report(line);
    }

    private static VehicleRig rig() {
        return new VehicleRig(VehicleRig.FLAT, TrainSpec.m983TractorDefaults(), START_X, START_Z, 0.0);
    }

    @Test
    @DisplayName("it settles on its eight wheels and stays where it was put")
    void settles() {
        try (VehicleRig rig = rig()) {
            rig.run(80);
            say("SETTLE m983: " + rig.telemetry());
            assertEquals(8, rig.vehicle.wheelsOnGround());
            JoltVehicle.Snapshot s = rig.vehicle.snapshot();
            assertTrue(Math.abs(s.x() - START_X) < 0.2 && Math.abs(s.z() - START_Z) < 0.5,
                    "it drifted to " + s.x() + " " + s.z());
            assertTrue(Math.abs(s.pitchDeg()) < 1.5 && Math.abs(s.rollDeg()) < 1.0,
                    "tilt " + s.pitchDeg() + " " + s.rollDeg());
        }
    }

    @Test
    @DisplayName("0 to 40 km/h takes a few seconds, and it climbs to its 80 km/h limit and stays under it")
    void accelerationAndLimit() {
        try (VehicleRig rig = rig()) {
            rig.run(60);
            double t0 = rig.seconds();
            rig.drive(1.0, 0.0, 0.0, 0.0);
            double t40 = -1.0;
            double top = 0.0;
            while (rig.seconds() - t0 < 90.0) {
                rig.run(1);
                double kmh = rig.forwardKmh();
                top = Math.max(top, kmh);
                if (t40 < 0.0 && kmh >= 40.0) {
                    t40 = rig.seconds() - t0;
                }
            }
            say(String.format(Locale.ROOT, "RESULT m983 alone: 0-40 km/h %.1f s, top %.1f km/h", t40, top));
            assertTrue(t40 > 1.5 && t40 < 10.0, "0-40 took " + t40);
            assertTrue(top > 70.0, "top speed " + top);
            assertTrue(top < 82.0, "the 80 km/h limit leaked: " + top);
        }
    }

    @Test
    @DisplayName("in reverse it stops at 15 km/h")
    void reverseLimit() {
        try (VehicleRig rig = rig()) {
            rig.run(60);
            double top = 0.0;
            double sum = 0.0;
            int n = 0;
            for (int i = 0; i < 20 * 25; i++) {
                rig.drive(-1.0, 0.0, 0.0, 0.0);
                rig.run(1);
                top = Math.max(top, -rig.forwardKmh());
                if (i >= 20 * 20) {
                    sum += -rig.forwardKmh();
                    n++;
                }
            }
            double steady = sum / n;
            say(String.format(Locale.ROOT, "RESULT m983 reverse: top %.1f km/h, steady %.1f km/h", top, steady));
            assertTrue(steady > 10.0 && steady < 16.5, "steady reverse speed " + steady);
            assertTrue(top < 20.0, "reverse top " + top);
        }
    }

    @Test
    @DisplayName("full lock at 15 km/h turns it in a tight circle, to the right and to the left")
    void turns() {
        for (double side : new double[] {1.0, -1.0}) {
            try (VehicleRig rig = rig()) {
                rig.run(60);
                for (int i = 0; i < 20 * 5; i++) {
                    rig.drive(Math.max(-0.3, Math.min(1.0, (15.0 - rig.forwardKmh()) * 0.3)), 0.0, 0.0, 0.0);
                    rig.run(1);
                }
                double yawStart = rig.vehicle.snapshot().yawDeg();
                double speedSum = 0.0;
                int n = 20 * 5;
                for (int i = 0; i < n; i++) {
                    rig.drive(Math.max(-0.3, Math.min(1.0, (15.0 - rig.forwardKmh()) * 0.3)), -side, 0.0, 0.0);
                    rig.run(1);
                    speedSum += rig.vehicle.speed();
                }
                double turned = Math.toRadians(Math.abs(PoseMath.wrapDegrees(rig.vehicle.snapshot().yawDeg() - yawStart)));
                double radius = (speedSum / n) * (n / 20.0) / Math.max(turned, 1.0e-6);
                say(String.format(Locale.ROOT, "RESULT m983 full lock %s at 15 km/h: radius %.1f m", side > 0 ? "right" : "left",
                        radius));
                assertTrue(radius > 6.0 && radius < 30.0, "radius " + radius);
            }
        }
    }

    @Test
    @DisplayName("the handbrake holds it still on the flat")
    void handbrake() {
        try (VehicleRig rig = rig()) {
            rig.run(60);
            for (int i = 0; i < 20 * 8; i++) {
                rig.drive(Math.max(-0.3, Math.min(1.0, (30.0 - rig.forwardKmh()) * 0.3)), 0.0, 0.0, 0.0);
                rig.run(1);
            }
            rig.drive(0.0, 0.0, 0.0, 1.0);
            int guard = 0;
            while (Math.abs(rig.forwardKmh()) > 0.5 && guard++ < 20 * 20) {
                rig.run(1);
            }
            assertTrue(guard < 20 * 20, "it never stopped");
            double z = rig.vehicle.snapshot().z();
            rig.run(60);
            assertEquals(z, rig.vehicle.snapshot().z(), 0.3, "a braked tractor holds");
        }
    }
}
