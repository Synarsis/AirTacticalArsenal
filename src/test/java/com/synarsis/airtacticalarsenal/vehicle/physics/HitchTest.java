package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.entity.patriot.M983Coupling;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The M983 tractor backing under a kingpin on the real Jolt")
class HitchTest {

    private static final double TRAILER_X = 100.5;
    private static final double TRAILER_Z = 100.5;
    private static final double PIN_Z = TRAILER_Z + 5.875;

    private static void say(String line) {
        VehicleRig.report(line);
    }

    private static TrainSpec[] specs() {
        return new TrainSpec[] {TrainSpec.launcherDefaults(), TrainSpec.radarDefaults()};
    }

    private static HitchRig rigAt(TrainSpec spec, double metresAhead, double lateral, double yawError) {
        HitchRig rig = new HitchRig(spec, TRAILER_X - lateral, PIN_Z + metresAhead, yawError);
        rig.placeFreeTrailer(TRAILER_X, TRAILER_Z, 0.0);
        return rig;
    }

    private static boolean backUnder(HitchRig rig, int maxTicks, double steerGain) {
        int still = 0;
        for (int i = 0; i < maxTicks; i++) {
            Vec3 saddle = rig.saddle();
            Vec3 pin = rig.kingpin();
            JoltVehicle.Snapshot s = rig.tractor.snapshot();
            double remaining = saddle.z - pin.z;
            double lateral = pin.x - saddle.x;
            double heading = -Math.toRadians(PoseMath.wrapDegrees(s.yawDeg()));
            double wanted = Math.max(-0.15, Math.min(0.15, -0.8 * lateral));
            double steer = Math.max(-1.0, Math.min(1.0, steerGain * (heading - wanted)));
            double reverseSpeed = -rig.tractor.forwardSpeed();
            double target = remaining > 0.12 ? Math.max(0.25, Math.min(1.4, 0.35 * remaining)) : 0.0;
            double throttle = 0.0;
            double brake = 0.0;
            if (target == 0.0) {
                brake = 1.0;
            } else {
                throttle = -Math.max(0.0, Math.min(0.5, 0.6 * (target - reverseSpeed)));
                if (reverseSpeed > target + 0.2) {
                    brake = 0.5;
                }
            }
            rig.drive(throttle, steer, brake, 0.0);
            rig.tick();
            boolean slow = Math.abs(rig.forwardKmh()) <= 0.8;
            if (rig.verdict() == M983Coupling.Verdict.READY && slow) {
                still++;
                if (still >= 10) {
                    return true;
                }
            } else {
                still = 0;
            }
        }
        return false;
    }

    @Test
    @DisplayName("from six metres the tractor backs to the kingpin without touching the trailer, and the capture conditions hold")
    void backsUnder() {
        double[][] starts = {{0.0, 0.0}, {0.25, 1.5}, {-0.25, -1.5}};
        for (TrainSpec spec : specs()) {
            for (double[] start : starts) {
                try (HitchRig rig = rigAt(spec, 6.0, start[0], start[1])) {
                    rig.run(60);
                    boolean reached = backUnder(rig, 20 * 90, 2.0);
                    say(String.format(Locale.ROOT, "RESULT approach %s lateral %.2f yaw %.1f: %s, worst hit %.2f m/s, %s",
                            spec.name, start[0], start[1], reached ? "reached" : "NOT reached", rig.worstImpact(),
                            rig.telemetry()));
                    assertTrue(reached, spec.name + " " + rig.telemetry());
                    assertTrue(rig.worstImpact() < 0.1, "it touched the trailer at " + rig.worstImpact() + " m/s");
                    assertEquals(M983Coupling.Verdict.READY, rig.verdict());
                }
            }
        }
    }

    @Test
    @DisplayName("without any steering the tractor still walks under the kingpin from four metres")
    void backsStraight() {
        for (TrainSpec spec : specs()) {
            try (HitchRig rig = rigAt(spec, 4.0, 0.0, 0.0)) {
                rig.run(60);
                boolean reached = false;
                for (int i = 0; i < 20 * 40 && !reached; i++) {
                    double remaining = rig.saddle().z - rig.kingpin().z;
                    double reverse = -rig.tractor.forwardSpeed();
                    double target = remaining > 0.12 ? Math.max(0.25, Math.min(1.2, 0.35 * remaining)) : 0.0;
                    rig.drive(target == 0.0 ? 0.0 : -Math.max(0.0, Math.min(0.4, 0.6 * (target - reverse))), 0.0,
                            target == 0.0 || reverse > target + 0.2 ? 0.6 : 0.0, 0.0);
                    rig.tick();
                    reached = rig.verdict() == M983Coupling.Verdict.READY && Math.abs(rig.forwardKmh()) < 0.8;
                }
                say("RESULT straight approach " + spec.name + ": " + rig.telemetry() + " hit " + rig.worstImpact());
                assertTrue(reached, rig.telemetry());
                assertTrue(rig.worstImpact() < 0.1, "touched at " + rig.worstImpact());
            }
        }
    }

    @Test
    @DisplayName("the body boxes leave a gap of at least 0.05 under the neck of the trailer")
    void gapUnderTheSaddle() {
        TrainSpec spec = TrainSpec.launcherDefaults();
        double saddleTop = 0.0;
        for (VehicleSpec.Box box : VehicleSpecs.m983Tractor().body.boxes) {
            if (box.centre[2] - box.half[2] < 0.6) {
                saddleTop = Math.max(saddleTop, box.centre[1] + box.half[1]);
            }
        }
        assertTrue(saddleTop < 1.25, "the tractor boxes at the saddle must stay under 1.25, got " + saddleTop);
        for (TrainSpec each : new TrainSpec[] {spec, TrainSpec.radarDefaults()}) {
            double neckBottom = Double.MAX_VALUE;
            for (VehicleSpec.Box box : each.trailer.body.boxes) {
                double from = box.centre[2] - box.half[2];
                double to = box.centre[2] + box.half[2];
                if (to > 4.125 && from < 6.5) {
                    neckBottom = Math.min(neckBottom, box.centre[1] - box.half[1]);
                }
            }
            assertTrue(neckBottom > 1.30, each.name + " neck bottom " + neckBottom);
            assertTrue(neckBottom - saddleTop >= 0.05, each.name + " gap " + (neckBottom - saddleTop));
        }
    }

    @Test
    @DisplayName("coupled, the train stands on twelve wheels with a straight hitch and a left turn bends it the right way")
    void coupledTrain() {
        for (TrainSpec spec : specs()) {
            try (HitchRig rig = rigAt(spec, 6.0, 0.0, 0.0)) {
                rig.run(40);
                assertTrue(backUnder(rig, 20 * 60, 2.0), rig.telemetry());
                rig.couple(true);
                assertTrue(rig.hitch.linked());
                rig.drive(0.0, 0.0, 0.0, 0.0);
                rig.run(80);
                assertEquals(8, rig.tractor.wheelsOnGround());
                assertEquals(4, rig.hitch.trailer().wheelsOnGround());
                double[] straight = rig.hitch.articulation();
                assertTrue(Math.abs(straight[0]) < 1.0 && Math.abs(straight[1]) < 2.0 && Math.abs(straight[2]) < 1.5,
                        "settled articulation " + straight[0] + " " + straight[1] + " " + straight[2]);
                for (int i = 0; i < 20 * 14; i++) {
                    rig.drive(Math.max(-0.3, Math.min(1.0, (15.0 - rig.forwardKmh()) * 0.3)), 1.0, 0.0, 0.0);
                    rig.tick();
                }
                double yaw = rig.tractor.snapshot().yawDeg();
                double bend = M983Coupling.bend((float) yaw, (float) rig.hitch.snapshot().yawDeg());
                double[] art = rig.hitch.articulation();
                say(String.format(Locale.ROOT, "RESULT coupled left turn %s: articulation %.1f, bend %.1f, %s",
                        spec.name, art[0], bend, rig.telemetry()));
                assertTrue(art[0] * bend > 0.0 && Math.abs(art[0] - bend) < 2.0,
                        "articulation " + art[0] + " and bend " + bend + " must agree");
                assertTrue(Math.abs(art[0]) > 8.0 && Math.abs(art[0]) < 62.0, "articulation " + art[0]);
            }
        }
    }

    @Test
    @DisplayName("over a ridge, a ditch and a one-sided ramp the hitch keeps its pitch and roll limits, whichever joint is used")
    void pitchAndRollLimits() {
        VehicleRig.Surface[] terrains = {
                (x, z) -> z >= 135 && z < 143 ? 64.0 + 0.5 * Math.sin(Math.PI * (z + 0.5 - 135) / 8.0) : 64.0,
                (x, z) -> z >= 135 && z < 143 ? 64.0 - 0.5 * Math.sin(Math.PI * (z + 0.5 - 135) / 8.0) : 64.0,
                (x, z) -> x >= 100 && z >= 130 ? 64.0 + 0.9 * Math.min(1.0, (z - 130) / 6.0) : 64.0,
                (x, z) -> x < 100 && z >= 130 && z < 140 ? 64.0 + 0.9 * Math.sin(Math.PI * (z - 130 + 0.5) / 10.0)
                        : 64.0};
        String[] names = {"ridge", "ditch", "ramp", "bump"};
        for (String type : new String[] {TrainSpec.SIX_DOF, TrainSpec.SWING_TWIST}) {
            for (int t = 0; t < terrains.length; t++) {
                TrainSpec spec = TrainSpec.launcherDefaults();
                spec.coupling.type = type;
                try (HitchRig rig = new HitchRig(spec, TRAILER_X, PIN_Z + 6.0, 0.0, terrains[t])) {
                    rig.placeFreeTrailer(TRAILER_X, TRAILER_Z, 0.0);
                    rig.run(40);
                    assertTrue(backUnder(rig, 20 * 60, 2.0), rig.telemetry());
                    rig.couple(true);
                    rig.run(40);
                    double worstPitch = 0.0;
                    double worstRoll = 0.0;
                    double worstYaw = 0.0;
                    for (int i = 0; i < 20 * 25; i++) {
                        rig.drive(Math.max(-0.3, Math.min(1.0, (10.0 - rig.forwardKmh()) * 0.3)), 0.0, 0.0, 0.0);
                        rig.tick();
                        double[] art = rig.hitch.articulation();
                        worstYaw = Math.max(worstYaw, Math.abs(art[0]));
                        worstPitch = Math.max(worstPitch, Math.abs(art[1]));
                        worstRoll = Math.max(worstRoll, Math.abs(art[2]));
                    }
                    say(String.format(Locale.ROOT, "RESULT hitch %s over a %s: yaw %.2f pitch %.2f roll %.2f, %s", type,
                            names[t], worstYaw, worstPitch, worstRoll, rig.telemetry()));
                    assertTrue(rig.tractor.snapshot().z() > PIN_Z + 20.0, "it should have crossed: " + rig.telemetry());
                    assertTrue(worstPitch <= spec.coupling.pitchLimitDegrees + 0.5, type + " " + names[t] + " pitch " + worstPitch);
                    assertTrue(worstRoll <= spec.coupling.rollLimitDegrees + 0.5, type + " " + names[t] + " roll " + worstRoll);
                }
            }
        }
    }

    @Test
    @DisplayName("couple, drive thirty metres with a turn, uncouple and drive off: 25 rounds leave no bodies and no slots")
    void rounds() {
        assertTrue(JoltRuntime.load(java.nio.file.Path.of("build", "test-natives")));
        TrainSpec spec = TrainSpec.launcherDefaults();
        try (HitchRig rig = rigAt(spec, 3.0, 0.0, 0.0)) {
            int baseBodies = 3;
            for (int round = 0; round < 25; round++) {
                rig.run(20);
                assertEquals(baseBodies, rig.world.bodyCount(), "round " + round + " before");
                boolean reached = backUnder(rig, 20 * 60, 2.0);
                assertTrue(reached, "round " + round + " " + rig.telemetry());
                rig.couple(true);
                assertEquals(baseBodies, rig.world.bodyCount(), "round " + round + " coupled");
                assertEquals(1, rig.world.slotsInUse(), "round " + round + " coupled");
                rig.run(20);
                JoltVehicle.Snapshot start = rig.tractor.snapshot();
                double travelled = 0.0;
                for (int i = 0; i < 20 * 40 && travelled < 30.0; i++) {
                    double turn = travelled > 10.0 && travelled < 20.0 ? 0.5 : 0.0;
                    rig.drive(Math.max(-0.3, Math.min(1.0, (15.0 - rig.forwardKmh()) * 0.3)), turn, 0.0, 0.0);
                    rig.tick();
                    JoltVehicle.Snapshot now = rig.tractor.snapshot();
                    travelled += Math.hypot(now.vx(), now.vz()) / 20.0;
                }
                assertTrue(travelled >= 29.0, "round " + round + " travelled " + travelled);
                rig.drive(0.0, 0.0, 1.0, 0.0);
                rig.run(80);
                assertTrue(Math.abs(rig.forwardKmh()) < 1.0, "round " + round + " did not stop");
                JoltVehicle.Snapshot parked = rig.hitch.snapshot();
                rig.uncouple();
                assertFalse(rig.hitch != null);
                assertEquals(baseBodies, rig.world.bodyCount(), "round " + round + " uncoupled");
                assertEquals(2, rig.world.slotsInUse(), "round " + round + " uncoupled");
                JoltVehicle.Snapshot here = rig.tractor.snapshot();
                double driven = 0.0;
                for (int i = 0; i < 20 * 30 && driven < 10.0; i++) {
                    rig.drive(0.6, 0.0, 0.0, 0.0);
                    rig.tick();
                    JoltVehicle.Snapshot now = rig.tractor.snapshot();
                    driven = Math.hypot(now.x() - here.x(), now.z() - here.z());
                }
                assertTrue(driven >= 10.0, "round " + round + " drove off only " + driven);
                JoltVehicle.Snapshot still = rig.freeTrailer.snapshot();
                assertEquals(parked.x(), still.x(), 1.0e-3, "the trailer stands, round " + round);
                assertEquals(parked.z(), still.z(), 1.0e-3);
                rig.drive(0.0, 0.0, 1.0, 0.0);
                rig.run(60);
                rig.tractor.teleport(TRAILER_X, HitchRig.GROUND + 0.05, PIN_Z + 3.0, 0.0, 0.0, 0.0,
                        new com.github.stephengold.joltjni.Vec3(0f, 0f, 0f),
                        new com.github.stephengold.joltjni.Vec3(0f, 0f, 0f));
                rig.freeTrailer.teleport(TRAILER_X, HitchRig.GROUND, TRAILER_Z, 0.0, 0.0, 0.0, null, null);
                rig.run(30);
            }
        }
    }
}
