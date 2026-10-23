package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The M983 tractor climbs like the launcher does")
class M983ClimbTest {

    private static final double START_X = 100.5;
    private static final double START_Z = 100.5;

    private static void say(String line) {
        VehicleRig.report(line);
    }

    private static double drive(VehicleSpec spec, VehicleRig.Surface surface, int seconds, double throttle) {
        return drive(spec, surface, seconds, throttle, 0.0);
    }

    private static double drive(VehicleSpec spec, VehicleRig.Surface surface, int seconds, double throttle,
            double offset) {
        try (VehicleRig rig = new VehicleRig(surface, spec, START_X, START_Z + offset, 0.0)) {
            rig.run(60);
            for (int i = 0; i < 20 * seconds; i++) {
                rig.drive(throttle, 0.0, 0.0, 0.0);
                rig.run(1);
            }
            return rig.vehicle.snapshot().z() - START_Z - offset;
        }
    }

    @Test
    @DisplayName("a step of one block")
    void step() {
        VehicleRig.Surface step = (x, z) -> z >= 118 ? 65.0 : 64.0;
        double tel = drive(VehicleSpec.defaults(), step, 20, 1.0);
        double tractor = drive(TrainSpec.m983TractorDefaults(), step, 20, 1.0);
        say(String.format(Locale.ROOT, "RESULT step 1 block: TEL %.1f m, M983 %.1f m", tel, tractor));
        assertTrue(tel > 15.0, "the launcher itself must climb it: " + tel);
        assertTrue(tractor > 15.0, "the tractor stopped at " + tractor);
    }

    @Test
    @DisplayName("a step of one block whichever way the ground cells fall against it")
    void stepAtAnyAlignment() {
        VehicleRig.Surface step = (x, z) -> z >= 118 ? 65.0 : 64.0;
        StringBuilder row = new StringBuilder();
        double worst = Double.MAX_VALUE;
        for (int index = 0; index < 8; index++) {
            double offset = index * 0.0625;
            double tractor = drive(TrainSpec.m983TractorDefaults(), step, 20, 1.0, offset);
            row.append(String.format(Locale.ROOT, "%.3f:%.1f ", offset, tractor));
            worst = Math.min(worst, tractor);
        }
        say("RESULT step alignments (offset:metres) " + row);
        assertTrue(worst > 15.0, "the tractor stopped at the step: " + row);
    }

    @Test
    @DisplayName("slopes of one block in three and four")
    void slope() {
        for (int run = 2; run <= 4; run++) {
            double length = run;
            VehicleRig.Surface slope = (x, z) -> z >= 118 ? 64.0 + Math.min(10, Math.floor((z - 118) / length) + 1)
                    : 64.0;
            double tel = drive(VehicleSpec.defaults(), slope, 40, 1.0);
            double tractor = drive(TrainSpec.m983TractorDefaults(), slope, 40, 1.0);
            say(String.format(Locale.ROOT, "RESULT slope 1:%d: TEL %.1f m, M983 %.1f m", run, tel, tractor));
            if (run >= 3) {
                assertTrue(tractor > 40.0, "the tractor stopped at " + tractor + " on a slope of 1:" + run);
            }
        }
    }

    @Test
    @DisplayName("it pulls away with its nose against a wall")
    void wall() {
        VehicleRig.Surface wall = (x, z) -> z >= 112 ? 70.0 : 64.0;
        double tel = drive(VehicleSpec.defaults(), wall, 8, -1.0);
        double tractor = drive(TrainSpec.m983TractorDefaults(), wall, 8, -1.0);
        say(String.format(Locale.ROOT, "RESULT reverse from a wall: TEL %.1f m, M983 %.1f m", tel, tractor));
        assertTrue(tel < -3.0, "the launcher backs away: " + tel);
        assertTrue(tractor < -3.0, "the tractor stays at " + tractor);
    }
}
