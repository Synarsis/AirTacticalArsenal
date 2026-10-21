package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The Patriot train on the real Jolt")
class JoltTrainTest {

    private static final double START_X = 100.5;
    private static final double START_Z = 100.5;

    private static void say(String line) {
        VehicleRig.report(line);
    }

    private static TrainRig flat(TrainSpec spec) {
        return new TrainRig(TrainRig.FLAT, spec, START_X, START_Z, 0.0);
    }

    @Test
    @DisplayName("it settles on its twelve wheels with a straight hitch")
    void settles() {
        for (TrainSpec spec : new TrainSpec[] {TrainSpec.launcherDefaults(), TrainSpec.radarDefaults()}) {
            try (TrainRig rig = flat(spec)) {
                rig.run(80);
                say("SETTLE " + spec.name + ": " + rig.telemetry());
                assertEquals(8, rig.train.tractor().wheelsOnGround());
                assertEquals(4, rig.train.trailer().wheelsOnGround());
                double[] a = rig.train.articulation();
                assertTrue(Math.abs(a[0]) < 0.5 && Math.abs(a[1]) < 1.5 && Math.abs(a[2]) < 1.0,
                        "articulation " + a[0] + " " + a[1] + " " + a[2]);
                JoltVehicle.Snapshot s = rig.train.tractor().snapshot();
                assertTrue(Math.abs(s.x() - START_X) < 0.2 && Math.abs(s.z() - START_Z) < 0.5,
                        "it should stay where it was put, now " + s.x() + " " + s.z());
            }
        }
    }

    private static void hold(TrainRig rig, double kmh, double toTheRight, int ticks) {
        for (int i = 0; i < ticks; i++) {
            rig.drive(Math.max(-0.3, Math.min(1.0, (kmh - rig.forwardKmh()) * 0.3)), -toTheRight, 0.0, 0.0);
            rig.run(1);
        }
    }

    @Test
    @DisplayName("0 to 40 km/h on the flat takes a few seconds, and the hitch stays straight")
    void accelerates() {
        for (TrainSpec spec : new TrainSpec[] {TrainSpec.launcherDefaults(), TrainSpec.radarDefaults()}) {
            try (TrainRig rig = flat(spec)) {
                rig.run(60);
                double t0 = rig.seconds();
                rig.drive(1.0, 0.0, 0.0, 0.0);
                double worst = 0.0;
                while (rig.forwardKmh() < 40.0 && rig.seconds() - t0 < 60.0) {
                    rig.run(1);
                    worst = Math.max(worst, Math.abs(rig.train.articulation()[0]));
                }
                double t40 = rig.seconds() - t0;
                say(String.format(Locale.ROOT, "RESULT %s 0-40 km/h: %.1f s, worst articulation %.2f deg", spec.name, t40, worst));
                assertTrue(t40 > 2.0 && t40 < 12.0, "0-40 took " + t40);
                assertTrue(worst < 3.0, "worst articulation " + worst);
            }
        }
    }

    @Test
    @DisplayName("full lock at 15 km/h bends the hitch the right way and holds it")
    void fullLock() {
        for (double side : new double[] {1.0, -1.0}) {
            for (TrainSpec spec : new TrainSpec[] {TrainSpec.launcherDefaults(), TrainSpec.radarDefaults()}) {
                try (TrainRig rig = flat(spec)) {
                    rig.run(60);
                    hold(rig, 15.0, 0.0, 60);
                    hold(rig, 15.0, side, 20 * 12);
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    double yawStart = rig.train.tractor().snapshot().yawDeg();
                    double speedSum = 0.0;
                    int n = 100;
                    for (int i = 0; i < n; i++) {
                        hold(rig, 15.0, side, 1);
                        double psi = rig.train.articulation()[0];
                        min = Math.min(min, psi);
                        max = Math.max(max, psi);
                        speedSum += rig.train.tractor().speed();
                    }
                    double yawEnd = rig.train.tractor().snapshot().yawDeg();
                    double turned = Math.toRadians(Math.abs(PoseMath.wrapDegrees(yawEnd - yawStart)));
                    double radius = (speedSum / n) * (n / 20.0) / Math.max(turned, 1.0e-6);
                    double mean = 0.5 * (min + max);
                    say(String.format(Locale.ROOT, "RESULT %s full lock %s at 15 km/h: articulation %.1f deg (spread %.2f), radius %.1f m, yaw %.1f to %.1f",
                            spec.name, side > 0 ? "right" : "left", mean, max - min, radius, yawStart, yawEnd));
                    assertTrue(Math.signum(mean) == -side, "a turn to the " + (side > 0 ? "right" : "left")
                            + " leaves the trailer pointing the other way, got " + mean);
                    assertTrue(Math.abs(mean) > 10.0 && Math.abs(mean) < 60.0, "articulation " + mean);
                    assertTrue(max - min < 3.0, "the angle should be steady, spread " + (max - min));
                    assertTrue(radius > 8.0 && radius < 40.0, "radius " + radius);
                }
            }
        }
    }

    @Test
    @DisplayName("reversing five metres in a straight line does not fold the hitch")
    void reverses() {
        for (TrainSpec spec : new TrainSpec[] {TrainSpec.launcherDefaults(), TrainSpec.radarDefaults()}) {
            try (TrainRig rig = flat(spec)) {
                rig.run(60);
                double z0 = rig.train.tractor().snapshot().z();
                double worst = 0.0;
                int guard = 0;
                while (Math.abs(rig.train.tractor().snapshot().z() - z0) < 5.0 && guard++ < 20 * 40) {
                    rig.drive(-1.0, 0.0, 0.0, 0.0);
                    rig.run(1);
                    worst = Math.max(worst, Math.abs(rig.train.articulation()[0]));
                }
                say(String.format(Locale.ROOT, "RESULT %s reverse 5 m: worst articulation %.2f deg in %.1f s", spec.name, worst, rig.seconds() - 3.0));
                assertTrue(guard < 20 * 40, "it never got 5 m back");
                assertTrue(worst < 15.0, "worst articulation " + worst);
            }
        }
    }

    @Test
    @DisplayName("a ridge and a ditch keep the relative pitch and roll inside the hitch limits")
    void ridgeAndDitch() {
        for (double depth : new double[] {1.0, -1.0}) {
            TrainSpec spec = TrainSpec.launcherDefaults();
            VehicleRig.Surface terrain = (x, z) -> z >= 130 && z < 138
                    ? 64.0 + depth * 0.5 * Math.sin(Math.PI * (z + 0.5 - 130) / 8.0) : 64.0;
            try (TrainRig rig = new TrainRig(terrain, spec, START_X, START_Z, 0.0)) {
                rig.run(60);
                double worstPitch = 0.0;
                double worstRoll = 0.0;
                double worstYaw = 0.0;
                for (int i = 0; i < 20 * 25; i++) {
                    rig.drive(Math.max(-0.3, Math.min(1.0, (10.0 - rig.forwardKmh()) * 0.3)), 0.0, 0.0, 0.0);
                    rig.run(1);
                    double[] a = rig.train.articulation();
                    worstYaw = Math.max(worstYaw, Math.abs(a[0]));
                    worstPitch = Math.max(worstPitch, Math.abs(a[1]));
                    worstRoll = Math.max(worstRoll, Math.abs(a[2]));
                }
                double z = rig.train.tractor().snapshot().z();
                say(String.format(Locale.ROOT, "RESULT %s 10 km/h: relative pitch %.1f deg, roll %.1f deg, yaw %.1f deg, ended at z %.1f",
                        depth > 0 ? "ridge" : "ditch", worstPitch, worstRoll, worstYaw, z));
                assertTrue(z > 140.0, "it should have crossed, z=" + z);
                assertTrue(worstPitch <= spec.coupling.pitchLimitDegrees + 0.5, "pitch " + worstPitch);
                assertTrue(worstRoll <= spec.coupling.rollLimitDegrees + 0.5, "roll " + worstRoll);
            }
        }
    }

    @Test
    @DisplayName("a jackknife in reverse stops at the yaw limit of the hitch, whichever joint is used, and the train recovers")
    void jackknife() {
        for (String type : new String[] {TrainSpec.SIX_DOF, TrainSpec.SWING_TWIST}) {
            TrainSpec spec = TrainSpec.launcherDefaults();
            spec.coupling.type = type;
            try (TrainRig rig = flat(spec)) {
                rig.run(60);
                double worstYaw = 0.0;
                double worstPitch = 0.0;
                double worstRoll = 0.0;
                for (int i = 0; i < 20 * 14; i++) {
                    rig.drive(-1.0, 1.0, 0.0, 0.0);
                    rig.run(1);
                    double[] a = rig.train.articulation();
                    worstYaw = Math.max(worstYaw, Math.abs(a[0]));
                    worstPitch = Math.max(worstPitch, Math.abs(a[1]));
                    worstRoll = Math.max(worstRoll, Math.abs(a[2]));
                }
                say(String.format(Locale.ROOT, "RESULT jackknife %s: yaw %.1f pitch %.1f roll %.1f, %s", type, worstYaw,
                        worstPitch, worstRoll, rig.telemetry()));
                assertTrue(worstYaw > 60.0 && worstYaw < spec.coupling.yawLimitDegrees + 4.0, type + " yaw " + worstYaw);
                assertTrue(worstPitch < spec.coupling.pitchLimitDegrees + 2.0, type + " pitch " + worstPitch);
                for (int i = 0; i < 20 * 20; i++) {
                    rig.drive(1.0, -1.0, 0.0, 0.0);
                    rig.run(1);
                }
                assertTrue(Math.abs(rig.train.articulation()[0]) < 50.0, type + " recovers: " + rig.telemetry());
            }
        }
    }

    @Test
    @DisplayName("the other joint is no worse on the main drives")
    void jointsCompared() {
        for (String type : new String[] {TrainSpec.SIX_DOF, TrainSpec.SWING_TWIST}) {
            TrainSpec spec = TrainSpec.launcherDefaults();
            spec.coupling.type = type;
            try (TrainRig rig = flat(spec)) {
                rig.run(60);
                hold(rig, 15.0, 0.0, 60);
                hold(rig, 15.0, 1.0, 20 * 12);
                double mean = rig.train.articulation()[0];
                double pitch = rig.train.articulation()[1];
                double roll = rig.train.articulation()[2];
                say(String.format(Locale.ROOT, "RESULT joint %s full lock right: yaw %.1f pitch %.2f roll %.2f", type, mean,
                        pitch, roll));
                assertTrue(mean < -10.0, type + " " + mean);
            }
        }
    }

    @Test
    @DisplayName("detach and attach: the link goes and comes back, poses are kept, and slots and bodies return to zero after 25 rounds")
    void detachAndAttachRounds() {
        assertTrue(JoltRuntime.load(java.nio.file.Path.of("build", "test-natives")));
        try (PhysicsWorld world = new PhysicsWorld(false, 1.6)) {
            for (int round = 0; round < 25; round++) {
                TrainSpec spec = round % 2 == 0 ? TrainSpec.launcherDefaults() : TrainSpec.radarDefaults();
                VehicleGround ground = new VehicleGround(world, 0, (x, z, low, high) -> 64.0);
                ground.update(100.0, 64.0, 100.0);
                JoltTrain train = new JoltTrain(world, spec, 100.5, 64.05, 100.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, true);
                train.tractor().setDriven(false);
                for (int i = 0; i < 40; i++) {
                    ground.update(100.5, 64.0, 100.5);
                    world.step();
                }
                assertEquals(3, world.bodyCount());
                assertEquals(1, world.slotsInUse());
                assertTrue(train.linked());
                JoltVehicle.Snapshot trailerBefore = train.trailer().snapshot();
                JoltVehicle.Snapshot tractorBefore = train.tractor().snapshot();

                train.detachTractor();
                assertFalse(train.linked());
                assertEquals(JoltVehicle.Mode.KINEMATIC, train.tractor().mode());
                assertEquals(JoltVehicle.Mode.STATIC, train.trailer().mode());
                for (int i = 1; i <= 40; i++) {
                    double along = PatriotTimingProbe.OFFSET_BLOCKS * i / 40.0;
                    train.tractor().moveKinematic(tractorBefore.x(), tractorBefore.y(), tractorBefore.z() + along,
                            tractorBefore.yawDeg(), tractorBefore.pitchDeg(), tractorBefore.rollDeg(), 0.05);
                    world.step();
                }
                JoltVehicle.Snapshot trailerAway = train.trailer().snapshot();
                assertEquals(trailerBefore.z(), trailerAway.z(), 1.0e-4, "the trailer stays put, round " + round);
                assertEquals(trailerBefore.y(), trailerAway.y(), 1.0e-4);
                JoltVehicle.Snapshot tractorAway = train.tractor().snapshot();
                assertEquals(tractorBefore.z() + PatriotTimingProbe.OFFSET_BLOCKS, tractorAway.z(), 0.05);

                for (int i = 1; i <= 40; i++) {
                    double along = PatriotTimingProbe.OFFSET_BLOCKS * (40 - i) / 40.0;
                    train.tractor().moveKinematic(tractorBefore.x(), tractorBefore.y(), tractorBefore.z() + along,
                            tractorBefore.yawDeg(), tractorBefore.pitchDeg(), tractorBefore.rollDeg(), 0.05);
                    world.step();
                }
                train.reattachTractor();
                assertTrue(train.linked());
                assertEquals(JoltVehicle.Mode.DYNAMIC, train.tractor().mode());
                assertEquals(JoltVehicle.Mode.DYNAMIC, train.trailer().mode());
                for (int i = 0; i < 40; i++) {
                    ground.update(100.5, 64.0, 100.5);
                    world.step();
                }
                double[] art = train.articulation();
                assertTrue(Math.abs(art[0]) < 1.0 && Math.abs(art[1]) < 2.0, "pose kept after the round trip: " + art[0] + " " + art[1]);
                assertEquals(8, train.tractor().wheelsOnGround());
                assertEquals(4, train.trailer().wheelsOnGround());

                train.close();
                ground.close();
                assertEquals(0, world.bodyCount(), "round " + round);
                assertEquals(0, world.slotsInUse(), "round " + round);
            }
        }
    }

    private static final class PatriotTimingProbe {
        static final double OFFSET_BLOCKS = 95.0 / 16.0;
    }

    @Test
    @DisplayName("the right key turns the train to the right: the yaw grows and the trailer is bent the other way")
    void rightKeyTurnsRight() {
        try (TrainRig rig = flat(TrainSpec.launcherDefaults())) {
            rig.run(60);
            double yawStart = rig.train.tractor().snapshot().yawDeg();
            for (int i = 0; i < 20 * 8; i++) {
                rig.train.tractor().setControls(com.synarsis.airtacticalarsenal.entity.patriot.PatriotJolt.controlsFor(
                        com.synarsis.airtacticalarsenal.entity.tel.TelInput.FWD
                                | com.synarsis.airtacticalarsenal.entity.tel.TelInput.RIGHT));
                rig.run(1);
            }
            double turned = PoseMath.wrapDegrees(rig.train.tractor().snapshot().yawDeg() - yawStart);
            double articulation = rig.train.articulation()[0];
            say(String.format(Locale.ROOT, "RESULT right key: yaw turned %.1f, articulation %.1f, %s", turned, articulation, rig.telemetry()));
            assertTrue(turned > 20.0, "the yaw of an entity grows when it turns right, got " + turned);
            assertTrue(articulation < -5.0, "the trailer lags to the other side, got " + articulation);
            for (int i = 0; i < 20 * 8; i++) {
                rig.train.tractor().setControls(com.synarsis.airtacticalarsenal.entity.patriot.PatriotJolt.controlsFor(
                        com.synarsis.airtacticalarsenal.entity.tel.TelInput.FWD
                                | com.synarsis.airtacticalarsenal.entity.tel.TelInput.LEFT));
                rig.run(1);
            }
            assertTrue(rig.train.articulation()[0] > -10.0, "the left key brings it back");
        }
    }

    @Test
    @DisplayName("the trailer takes the brakes of the tractor, and a parked train holds")
    void trailerBrakes() {
        try (TrainRig rig = flat(TrainSpec.launcherDefaults())) {
            rig.run(60);
            hold(rig, 25.0, 0.0, 20 * 10);
            rig.train.tractor().setControls(new JoltVehicle.Controls(0.0, 0.0, 0.0, 1.0));
            rig.run(1);
            assertEquals(1.0, rig.train.tractor().appliedHandbrake(), 1.0e-9);
            assertEquals(rig.train.tractor().appliedHandbrake(), rig.train.trailer().appliedHandbrake(), 1.0e-9);
            double z0 = rig.train.tractor().snapshot().z();
            double v0 = rig.forwardKmh();
            double worst = 0.0;
            int ticks = 0;
            while (rig.forwardKmh() > 0.5 && ticks++ < 20 * 20) {
                rig.run(1);
                worst = Math.max(worst, Math.abs(rig.train.articulation()[0]));
            }
            double distance = rig.train.tractor().snapshot().z() - z0;
            say(String.format(Locale.ROOT, "RESULT handbrake from %.1f km/h: %.1f m, worst articulation %.2f deg", v0, distance, worst));
            assertTrue(ticks < 20 * 20, "it never stopped");
            assertTrue(worst < 5.0, "the hitch folded while braking: " + worst);

            rig.train.tractor().setDriven(false);
            rig.train.tractor().setControls(JoltVehicle.Controls.IDLE);
            rig.run(60);
            assertEquals(1.0, rig.train.trailer().appliedHandbrake(), 1.0e-9, "an undriven train is parked");
            double z1 = rig.train.tractor().snapshot().z();
            rig.run(60);
            assertEquals(z1, rig.train.tractor().snapshot().z(), 0.05, "a parked train holds");
        }
    }
}
