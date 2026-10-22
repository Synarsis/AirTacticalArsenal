package com.synarsis.airtacticalarsenal.entity.patriot;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Reading the combined vehicle of stage 7")
class StageSevenHitchTest {

    private static StageSevenHitch at(double x, double y, double z, float yaw, float pitch, float roll, float bend,
            boolean out) {
        return new StageSevenHitch(x, y, z, yaw, pitch, roll, bend, out);
    }

    @Test
    @DisplayName("only a save that carries the tractor-out flag of the old cycle is of stage 7")
    void detection() {
        CompoundTag old = new CompoundTag();
        CompoundTag cycle = new CompoundTag();
        cycle.putBoolean("Supports", true);
        cycle.putBoolean("TractorOut", false);
        old.put("Cycle", cycle);
        assertTrue(StageSevenHitch.isStageSeven(old));

        CompoundTag current = new CompoundTag();
        CompoundTag fresh = new CompoundTag();
        fresh.putBoolean("Supports", true);
        current.put("Cycle", fresh);
        assertFalse(StageSevenHitch.isStageSeven(current));
        assertFalse(StageSevenHitch.isStageSeven(new CompoundTag()));
    }

    @Test
    @DisplayName("a coupled tractor stands so that its saddle is on the kingpin, and the trailer pose comes back unchanged")
    void coupledPose() {
        float[][] poses = {{0.0f, 0.0f, 0.0f}, {37.0f, 0.0f, 0.0f}, {-120.0f, 4.0f, -3.0f}, {179.0f, -6.0f, 5.0f}};
        for (float[] p : poses) {
            StageSevenHitch old = at(100.5, 64.0, -20.25, p[0], p[1], p[2], 0.0f, false);
            double[] tractor = old.tractorPose();
            assertEquals(p[0], tractor[3], 1.0E-6);
            double[] trailer = M983Coupling.trailerPoseFor(tractor[0], tractor[1], tractor[2], (float) tractor[3],
                    (float) tractor[4], (float) tractor[5], 0.0);
            assertEquals(100.5, trailer[0], 1.0E-6, "x for yaw " + p[0]);
            assertEquals(64.0, trailer[1], 1.0E-6, "y for yaw " + p[0]);
            assertEquals(-20.25, trailer[2], 1.0E-6, "z for yaw " + p[0]);
        }
    }

    @Test
    @DisplayName("with the hitch bent the tractor keeps the heading of the entity and the trailer swings by the bend")
    void bentPose() {
        StageSevenHitch old = at(10.0, 70.0, 10.0, 90.0f, 0.0f, 0.0f, -20.0f, false);
        double[] tractor = old.tractorPose();
        double[] trailer = M983Coupling.trailerPoseFor(tractor[0], tractor[1], tractor[2], (float) tractor[3],
                (float) tractor[4], (float) tractor[5], old.bend());
        assertEquals(70.0, trailer[3], 1.0E-4);
        net.minecraft.world.phys.Vec3 saddle = M983Coupling.worldOf(tractor[0], tractor[1], tractor[2],
                (float) tractor[3], (float) tractor[4], (float) tractor[5], M983Coupling.saddleAnchor());
        assertEquals(0.0, saddle.distanceTo(M983Coupling.worldOf(trailer[0], trailer[1], trailer[2],
                (float) trailer[3], (float) trailer[4], (float) trailer[5], M983Coupling.kingpinAnchor())), 1.0E-6);
    }

    @Test
    @DisplayName("a detached tractor stands 95 px further ahead along the heading")
    void detachedPose() {
        StageSevenHitch coupled = at(0.0, 64.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, false);
        StageSevenHitch out = at(0.0, 64.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, true);
        assertEquals(5.875, coupled.tractorPose()[2], 1.0E-9);
        assertEquals(5.875 + 95.0 / 16.0, out.tractorPose()[2], 1.0E-9);
        StageSevenHitch turned = at(0.0, 64.0, 0.0, 90.0f, 0.0f, 0.0f, 0.0f, true);
        assertEquals(-(5.875 + 95.0 / 16.0), turned.tractorPose()[0], 1.0E-6);
        assertEquals(0.0, turned.tractorPose()[2], 1.0E-6);
    }
}
