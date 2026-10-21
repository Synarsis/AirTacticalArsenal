package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Jolt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The 9P78-1 on the real Jolt")
class JoltVehicleDriveTest {

    private static final double START_X = 100.5;
    private static final double START_Z = 100.5;

    private static void say(String line) {
        VehicleRig.report(line);
    }

    private static VehicleRig flat() {
        return new VehicleRig(VehicleRig.FLAT, START_X, START_Z, 0.0);
    }

    private static void settle(VehicleRig rig) {
        rig.run(60);
    }

    private static void reach(VehicleRig rig, double kmh) {
        rig.drive(1.0, 0.0, 0.0, 0.0);
        while (rig.forwardKmh() < kmh && rig.seconds() < 60.0) {
            rig.run(1);
        }
    }

    private static void holdSpeed(VehicleRig rig, double kmh, int seconds) {
        for (int i = 0; i < 20 * seconds; i++) {
            rig.drive(Math.max(-0.2, Math.min(1.0, (kmh - rig.forwardKmh()) * 0.3)), 0.0, 0.0, 0.0);
            rig.run(1);
        }
    }

    @Test
    @DisplayName("0 to 60 km/h on the flat takes 8 to 10 seconds and the top speed is held near 65")
    void accelerates() {
        try (VehicleRig rig = flat()) {
            settle(rig);
            double t0 = rig.seconds();
            reach(rig, 60.0);
            double t60 = rig.seconds() - t0;
            rig.run(20 * 30);
            double top = rig.forwardKmh();
            say(String.format(Locale.ROOT, "RESULT 0-60 km/h: %.1f s; top speed %.1f km/h", t60, top));
            assertTrue(t60 >= 8.0 && t60 <= 10.0, "0-60 took " + t60 + " s");
            assertTrue(top >= 62.0 && top <= 66.0, "top speed " + top);
            assertEquals(8, rig.vehicle.wheelsOnGround());
        }
    }

    @Test
    @DisplayName("braking from 60 km/h stops in 20 to 25 metres, the nose dips")
    void brakes() {
        try (VehicleRig rig = flat()) {
            settle(rig);
            reach(rig, 60.0);
            rig.drive(0.0, 0.0, 0.0, 0.0);
            rig.run(1);
            double z0 = rig.vehicle.snapshot().z();
            double v0 = rig.forwardKmh();
            rig.drive(-1.0, 0.0, 0.0, 0.0);
            double dip = 0;
            double t0 = rig.seconds();
            while (rig.forwardKmh() > 0.5 && rig.seconds() - t0 < 20.0) {
                rig.run(1);
                dip = Math.max(dip, rig.vehicle.snapshot().pitchDeg());
            }
            double distance = rig.vehicle.snapshot().z() - z0;
            say(String.format(Locale.ROOT, "RESULT braking from %.1f km/h: %.1f m in %.1f s, nose dip %.2f deg", v0, distance, rig.seconds() - t0, dip));
            assertTrue(distance >= 20.0 && distance <= 25.0, "braking distance " + distance);
            assertTrue(dip > 0.3, "the nose should dip, got " + dip);
        }
    }

    @Test
    @DisplayName("a block-high step is climbed from 10 km/h")
    void climbsStep() {
        try (VehicleRig rig = new VehicleRig((x, z) -> z >= 140 ? 65.0 : 64.0, START_X, START_Z, 0.0)) {
            settle(rig);
            holdSpeed(rig, 10.0, 30);
            JoltVehicle.Snapshot s = rig.vehicle.snapshot();
            say(String.format(Locale.ROOT, "RESULT 1 block step at 10 km/h: z=%.1f y=%.2f v=%.1f km/h", s.z(), s.y(), rig.forwardKmh()));
            assertTrue(s.y() > 64.9, "should be on top of the step, y=" + s.y());
            assertTrue(s.z() > 145.0, "should be past the edge, z=" + s.z());
            assertTrue(rig.forwardKmh() > 5.0, "should keep rolling, v=" + rig.forwardKmh());
        }
    }

    @Test
    @DisplayName("a two block wall stops it")
    void stopsAtWall() {
        try (VehicleRig rig = new VehicleRig((x, z) -> z >= 140 ? 66.0 : 64.0, START_X, START_Z, 0.0)) {
            settle(rig);
            holdSpeed(rig, 10.0, 30);
            JoltVehicle.Snapshot s = rig.vehicle.snapshot();
            say(String.format(Locale.ROOT, "RESULT 2 block wall at 10 km/h: z=%.2f y=%.2f v=%.2f km/h", s.z(), s.y(), rig.forwardKmh()));
            assertTrue(s.z() + 7.88 <= 140.6, "the nose should be at the wall, z=" + s.z());
            assertTrue(s.y() < 64.5, "should not climb, y=" + s.y());
            assertTrue(Math.abs(rig.forwardKmh()) < 1.0);
        }
    }

    @Test
    @DisplayName("a smooth 15 degree climb is held at 35 to 45 km/h")
    void climbsHill() {
        double t = Math.tan(Math.toRadians(15.0));
        VehicleRig.Smooth hill = (x, z) -> z >= 120.0 ? 64.0 + (z - 120.0) * t : 64.0;
        try (VehicleRig rig = new VehicleRig(hill, VehicleSpec.defaults(), START_X, START_Z, 0.0)) {
            settle(rig);
            double sum = 0;
            int n = 0;
            for (int i = 0; i < 20 * 24; i++) {
                rig.drive(1.0, 0.0, 0.0, 0.0);
                rig.run(1);
                if (i > 20 * 14) {
                    sum += rig.forwardKmh();
                    n++;
                }
            }
            double mean = sum / n;
            say(String.format(Locale.ROOT, "RESULT 15 deg climb: %.1f km/h", mean));
            assertTrue(mean >= 35.0 && mean <= 45.0, "climb speed " + mean);
        }
    }

    @Test
    @DisplayName("full lock at 60 km/h rolls it no more than 6 degrees and it stays on its wheels")
    void fullLock() {
        try (VehicleRig rig = flat()) {
            settle(rig);
            reach(rig, 60.0);
            double maxRoll = 0;
            double minUp = 1.0;
            for (int dir : new int[] {1, -1}) {
                rig.drive(0.6, dir, 0.0, 0.0);
                for (int i = 0; i < 20 * 8; i++) {
                    rig.run(1);
                    JoltVehicle.Snapshot s = rig.vehicle.snapshot();
                    maxRoll = Math.max(maxRoll, Math.abs(s.rollDeg()));
                    minUp = Math.min(minUp, Math.cos(Math.toRadians(s.rollDeg())) * Math.cos(Math.toRadians(s.pitchDeg())));
                }
            }
            say(String.format(Locale.ROOT, "RESULT full lock at 60 km/h: max roll %.2f deg, v=%.1f km/h", maxRoll, rig.forwardKmh()));
            assertTrue(maxRoll <= 6.0, "roll " + maxRoll);
            assertTrue(minUp > 0.95);
            assertTrue(rig.vehicle.wheelsOnGround() >= 6);
        }
    }

    @Test
    @DisplayName("reverse is limited to about 20 km/h")
    void reverses() {
        try (VehicleRig rig = flat()) {
            settle(rig);
            rig.drive(-1.0, 0.0, 0.0, 0.0);
            rig.run(20 * 25);
            double v = -rig.forwardKmh();
            say(String.format(Locale.ROOT, "RESULT reverse: %.1f km/h", v));
            assertTrue(v >= 17.0 && v <= 21.0, "reverse speed " + v);
        }
    }

    @Test
    @DisplayName("native objects are all given back when the world and the vehicle are closed")
    void noLeaks() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        int before = Jolt.countNews() - Jolt.countDeletes();
        for (int round = 0; round < 3; round++) {
            try (VehicleRig rig = flat()) {
                rig.run(30);
                rig.drive(1.0, 0.3, 0.0, 0.0);
                rig.run(40);
                assertEquals(2, rig.world.bodyCount());
            }
        }
        System.gc();
        int after = Jolt.countNews() - Jolt.countDeletes();
        say("RESULT native objects before " + before + ", after " + after);
        assertTrue(after - before <= 2, "native objects left behind: " + (after - before));
    }

    @Test
    @DisplayName("an unmanned vehicle on the flat stays where it is and a heavy push does not move a jacked one")
    void parkedAndJacked() {
        try (VehicleRig rig = flat()) {
            settle(rig);
            rig.vehicle.setDriven(false);
            rig.run(100);
            double z0 = rig.vehicle.snapshot().z();
            rig.run(100);
            assertEquals(z0, rig.vehicle.snapshot().z(), 0.05);
            rig.vehicle.setMode(JoltVehicle.Mode.STATIC);
            JoltVehicle.Snapshot jacked = rig.vehicle.snapshot();
            rig.vehicle.addImpulse(2.0e6, 0.0, 2.0e6);
            rig.run(60);
            JoltVehicle.Snapshot after = rig.vehicle.snapshot();
            assertEquals(jacked.x(), after.x(), 1.0e-6);
            assertEquals(jacked.z(), after.z(), 1.0e-6);
            rig.vehicle.setMode(JoltVehicle.Mode.DYNAMIC);
            rig.vehicle.addImpulse(2.0e5, 0.0, 0.0);
            rig.run(40);
            assertTrue(Math.abs(rig.vehicle.snapshot().x() - jacked.x()) > 0.01, "should be movable again");
        }
    }
}
