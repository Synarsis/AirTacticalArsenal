package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.RRayCast;
import com.github.stephengold.joltjni.RayCastResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Ground under a vehicle in the Jolt world")
class VehicleGroundTest {

    private static final class World implements BlockColumns {
        final BlockColumns base = GroundPatchTimingTest.hills();
        final Map<Long, Double> edits = new HashMap<>();

        void put(int x, int z, double top) {
            this.edits.put(((long) x << 32) | (z & 0xFFFFFFFFL), top);
        }

        @Override
        public double solidTop(double x, double z, double low, double high) {
            Double edit = this.edits.get(((long) Math.floor(x) << 32) | ((long) Math.floor(z) & 0xFFFFFFFFL));
            if (edit != null) {
                return edit >= low && edit <= high ? edit : Double.NaN;
            }
            return this.base.solidTop(x, z, low, high);
        }
    }

    private static double castDown(PhysicsWorld world, double x, double z) {
        RRayCast ray = new RRayCast(x, 90.0, z, 0f, -60f, 0f);
        RayCastResult hit = new RayCastResult();
        if (!world.system().getNarrowPhaseQuery().castRay(ray, hit)) {
            return Double.NaN;
        }
        return 90.0 - 60.0 * hit.getFraction();
    }

    private static double worstGap(PhysicsWorld world, GroundPatch patch, double cx, double cz) {
        double worst = 0.0;
        for (int iz = 1; iz < patch.count() - 1; iz++) {
            double z = patch.nodeZ(iz) + 0.0001;
            if (Math.abs(z - cz) > 12.0) {
                continue;
            }
            for (int ix = 1; ix < patch.count() - 1; ix++) {
                double x = patch.nodeX(ix) + 0.0001;
                if (Math.abs(x - cx) > 12.0) {
                    continue;
                }
                float expected = patch.node(ix, iz);
                boolean surrounded = expected != GroundPatch.HOLE && patch.node(ix + 1, iz) != GroundPatch.HOLE
                        && patch.node(ix, iz + 1) != GroundPatch.HOLE && patch.node(ix + 1, iz + 1) != GroundPatch.HOLE;
                if (!surrounded) {
                    continue;
                }
                double actual = castDown(world, x, z);
                if (Double.isNaN(actual)) {
                    VehicleRig.report("miss at node " + ix + "," + iz + " height " + expected + " at " + x + "," + z + " window " + patch.low() + ".." + patch.high());
                    return 99.0;
                }
                worst = Math.max(worst, Math.abs(expected - actual));
            }
        }
        return worst;
    }

    @Test
    @DisplayName("a changed block and a move of the patch are written into the body in place and the body matches the data")
    void updatesInPlace() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        World blocks = new World();
        try (PhysicsWorld world = new PhysicsWorld(false, 1.6); VehicleGround ground = new VehicleGround(world, 0, blocks)) {
            assertTrue(ground.update(100.0, 64.0, 100.0));
            assertEquals(1, ground.fullRebuilds());
            int bodies = world.bodyCount();
            assertTrue(worstGap(world, ground.patch(), 100.0, 100.0) < 0.03, "gap " + worstGap(world, ground.patch(), 100.0, 100.0));

            blocks.put(101, 99, 67.0);
            blocks.put(102, 99, 62.0);
            assertTrue(ground.markChanged(101, 66, 99));
            assertTrue(ground.markChanged(102, 62, 99));
            assertFalse(ground.markChanged(101, 90, 99), "a block far above the window does not count");
            assertFalse(ground.markChanged(500, 66, 99), "a block outside the patch does not count");
            assertTrue(ground.update(100.0, 64.0, 100.0));
            assertEquals(1, ground.fullRebuilds());
            assertEquals(bodies, world.bodyCount());
            double gap = worstGap(world, ground.patch(), 100.0, 100.0);
            assertTrue(gap < 0.03, "gap after a block change " + gap);
            assertEquals(64.0 + 3.0, castDown(world, 101.25, 99.25), 0.8);

            assertTrue(ground.update(109.5, 64.0, 91.0));
            assertEquals(1, ground.fullRebuilds());
            gap = worstGap(world, ground.patch(), 109.5, 91.0);
            assertTrue(gap < 0.03, "gap after moving the patch " + gap);
            GroundPatch fresh = GroundPatchBuilder.build(blocks, 109.5, 64.0, 91.0);
            for (int iz = 0; iz < fresh.count(); iz += 7) {
                for (int ix = 0; ix < fresh.count(); ix += 7) {
                    assertEquals(fresh.node(ix, iz), ground.patch().node(ix, iz), 0.0);
                }
            }
            assertFalse(ground.update(109.5, 64.0, 91.0), "nothing changed, nothing to do");

            assertTrue(ground.update(109.5, 71.0, 91.0));
            assertEquals(2, ground.fullRebuilds(), "a new height window needs a new body");
            assertEquals(bodies, world.bodyCount());
        }
    }

    @Test
    @DisplayName("an explosion that changes hundreds of blocks becomes one full rebuild")
    void bigChangeIsRebuilt() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        World blocks = new World();
        try (PhysicsWorld world = new PhysicsWorld(false, 1.6); VehicleGround ground = new VehicleGround(world, 0, blocks)) {
            ground.update(100.0, 64.0, 100.0);
            for (int x = 90; x < 110; x++) {
                for (int z = 90; z < 110; z++) {
                    blocks.put(x, z, 60.0);
                    ground.markChanged(x, 60, z);
                }
            }
            ground.update(100.0, 64.0, 100.0);
            assertEquals(2, ground.fullRebuilds());
            assertTrue(worstGap(world, ground.patch(), 100.0, 100.0) < 0.03, "gap " + worstGap(world, ground.patch(), 100.0, 100.0));
        }
    }

    @Test
    @DisplayName("timings of the full build, a block change and a move of 8 metres, with the body written")
    void timings() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        World blocks = new World();
        try (PhysicsWorld world = new PhysicsWorld(false, 1.6); VehicleGround ground = new VehicleGround(world, 0, blocks)) {
            for (int i = 0; i < 5; i++) {
                ground.update(100.0 + i, 64.0, 100.0);
            }
            double[] full = new double[15];
            for (int i = 0; i < full.length; i++) {
                long started = System.nanoTime();
                GroundBody body = new GroundBody(world, 0, GroundPatchBuilder.build(blocks, 100.0 + i, 64.0, 100.0));
                full[i] = (System.nanoTime() - started) / 1.0E6;
                body.close();
            }
            double[] point = new double[40];
            for (int i = 0; i < point.length; i++) {
                blocks.put(100 + i % 7, 100 + i % 5, 63.0 + i % 4);
                ground.markChanged(100 + i % 7, 63, 100 + i % 5);
                ground.update(100.0, 64.0, 100.0);
                point[i] = ground.lastBuildMillis();
            }
            double[] moves = new double[20];
            double x = 100.0;
            for (int i = 0; i < moves.length; i++) {
                x += 9.0;
                ground.update(x, 64.0, 100.0);
                moves[i] = ground.lastBuildMillis();
            }
            Arrays.sort(full);
            Arrays.sort(point);
            Arrays.sort(moves);
            VehicleRig.report(String.format(Locale.ROOT,
                    "TIMING with Jolt body: full build and new body median %.2f ms; one block changed in place median %.2f ms (worst %.2f); patch moved by 9 m in place median %.2f ms (worst %.2f)",
                    full[full.length / 2], point[point.length / 2], point[point.length - 1], moves[moves.length / 2], moves[moves.length - 1]));
            assertTrue(point[point.length / 2] < full[full.length / 2]);
        }
    }
}
