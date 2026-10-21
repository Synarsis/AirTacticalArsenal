package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.client.fx.light.VoxelShadowMath.Cells;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The shadow of blocks (SPEC section 15.8)")
class VoxelShadowMathTest {

    private static final class World implements Cells {
        private final Map<Long, Integer> cells = new HashMap<>();

        World put(int x, int y, int z, int opacity) {
            this.cells.put(key(x, y, z), opacity);
            return this;
        }

        static long key(int x, int y, int z) {
            return ((long) x << 40) ^ ((long) y << 20) ^ z;
        }

        @Override
        public int opacity(int x, int y, int z) {
            return this.cells.getOrDefault(key(x, y, z), 0);
        }
    }

    @Test
    @DisplayName("a single full block between the point and the light gives no light at all")
    void wallBlocks() {
        World wall = new World().put(10, 5, 5, 255);
        assertEquals(0.0, VoxelShadowMath.shadowRay(wall, 5.5, 5.5, 5.5, 15.5, 5.5, 5.5), 0.0);
        assertEquals(1.0, VoxelShadowMath.shadowRay(wall, 5.5, 8.5, 5.5, 15.5, 8.5, 5.5), 0.0);
        assertEquals(0.0, VoxelShadowMath.shadowRay(wall, 5.5, 4.6, 5.5, 15.5, 6.4, 5.5), 0.0);
    }

    @Test
    @DisplayName("leaves let part of the light through, glass and air all of it")
    void partialAndClear() {
        World leaf = new World().put(10, 5, 5, OccupancyVolume.LEAVES);
        double through = VoxelShadowMath.shadowRay(leaf, 5.5, 5.5, 5.5, 15.5, 5.5, 5.5);
        assertEquals(1.0 - OccupancyVolume.LEAVES / 255.0, through, 1.0e-9);
        assertTrue(through > 0.3 && through < 0.6, "about 45% gets through one block of leaves: " + through);

        World two = new World().put(10, 5, 5, OccupancyVolume.LEAVES).put(11, 5, 5, OccupancyVolume.LEAVES);
        assertEquals(through * through, VoxelShadowMath.shadowRay(two, 5.5, 5.5, 5.5, 15.5, 5.5, 5.5), 1.0e-9);

        assertEquals(1.0, VoxelShadowMath.shadowRay(new World().put(10, 5, 5, 0), 5.5, 5.5, 5.5, 15.5, 5.5, 5.5), 0.0);
        assertEquals(1.0, VoxelShadowMath.shadowRay(new World(), 5.5, 5.5, 5.5, 15.5, 5.5, 5.5), 0.0);
    }

    @Test
    @DisplayName("a slab is stopped less than a full block but more than leaves")
    void slabs() {
        World slab = new World().put(10, 5, 5, OccupancyVolume.PLATE);
        double through = VoxelShadowMath.shadowRay(slab, 5.5, 5.5, 5.5, 15.5, 5.5, 5.5);
        assertEquals(1.0 - OccupancyVolume.PLATE / 255.0, through, 1.0e-9);
        assertTrue(through < 1.0 - OccupancyVolume.LEAVES / 255.0);
    }

    @Test
    @DisplayName("a light outside the volume, and a ray that leaves it, are free; so is a ray that starts outside")
    void outsideIsFree() {
        World wall = new World().put(120, 5, 5, 255);
        double beyond = VoxelShadowMath.shadowRay(wall, 100.5, 5.5, 5.5, 140.5, 5.5, 5.5);
        assertEquals(0.0, beyond, 0.0, "the wall inside the volume still stops it");
        assertEquals(1.0, VoxelShadowMath.shadowRay(new World(), 64.5, 30.5, 64.5, 300.5, 30.5, 64.5), 0.0);
        assertEquals(1.0, VoxelShadowMath.shadowRay(wall, -20.5, 5.5, 5.5, -60.5, 5.5, 5.5), 0.0);
        assertEquals(0.0, VoxelShadowMath.occupancyAt(new World().put(-1, 0, 0, 255), -1, 0, 0), 0.0);
        assertEquals(0.0, VoxelShadowMath.occupancyAt(new World().put(0, 64, 0, 255), 0, 64, 0), 0.0);
        assertEquals(0.0, VoxelShadowMath.occupancyAt(new World().put(128, 0, 0, 255), 128, 0, 0), 0.0);
        assertEquals(1.0, VoxelShadowMath.occupancyAt(new World().put(127, 63, 127, 255), 127, 63, 127), 0.0);
    }

    @Test
    @DisplayName("the last 0.6 blocks before the light are not looked at")
    void nearTheLight() {
        World world = new World().put(15, 5, 5, 255);
        assertEquals(1.0, VoxelShadowMath.shadowRay(world, 5.5, 5.5, 5.5, 15.5, 5.5, 5.5), 0.0,
                "the light is inside the block, its last 0.5 blocks are free");
        World front = new World().put(14, 5, 5, 255);
        assertEquals(0.0, VoxelShadowMath.shadowRay(front, 5.5, 5.5, 5.5, 15.5, 5.5, 5.5), 0.0);
        assertEquals(1.0, VoxelShadowMath.shadowRay(new World().put(5, 5, 5, 255), 5.1, 5.5, 5.5, 5.6, 5.5, 5.5), 0.0);
    }

    @Test
    @DisplayName("a big point light, a blast in a crater, is free for its last 1.5 blocks; a small one and a spot for 0.6")
    void bigLightsAreFreerNearTheCentre() {
        assertEquals(1.5, VoxelShadowMath.freeNearLight(false, 96.0), 0.0);
        assertEquals(1.5, VoxelShadowMath.freeNearLight(false, 30.0), 0.0);
        assertEquals(0.6, VoxelShadowMath.freeNearLight(false, 29.9), 0.0);
        assertEquals(0.6, VoxelShadowMath.freeNearLight(true, 36.0), 0.0, "a spot is a lamp, whatever its reach");
        World crater = new World().put(14, 5, 5, 255);
        double[] p = {2.0, 5.5, 5.5};
        double[] centre = {15.2, 5.5, 5.5};
        assertEquals(0.0, VoxelShadowMath.blockShadow(crater, p, centre, 1, 0.0), 0.0,
                "with the small free stretch the earth beside the centre puts everything in shadow");
        assertEquals(1.0, VoxelShadowMath.blockShadow(crater, p, centre, 1, 0.0,
                VoxelShadowMath.freeNearLight(false, 96.0)), 0.0, "with the big one it does not");
        World wall = new World().put(10, 5, 5, 255);
        assertEquals(0.0, VoxelShadowMath.blockShadow(wall, p, centre, 1, 0.0,
                VoxelShadowMath.freeNearLight(false, 96.0)), 0.0);
    }

    @Test
    @DisplayName("the walk is not longer than 64 blocks and 96 cells")
    void limits() {
        World far = new World().put(90, 5, 5, 255);
        assertEquals(1.0, VoxelShadowMath.shadowRay(far, 5.5, 5.5, 5.5, 120.5, 5.5, 5.5), 0.0);
        World near = new World().put(65, 5, 5, 255);
        assertEquals(0.0, VoxelShadowMath.shadowRay(near, 5.5, 5.5, 5.5, 120.5, 5.5, 5.5), 0.0);
        World diagonal = new World();
        for (int i = 0; i < 128; i++) {
            diagonal.put(i, i % 64, i, 255);
        }
        double result = VoxelShadowMath.shadowRay(diagonal, 0.5, 0.5, 0.5, 0.5 + 60.0, 0.5 + 60.0, 0.5 + 60.0);
        assertTrue(result >= 0.0 && result <= 1.0);
    }

    @Test
    @DisplayName("a block does not shade the point on its own face: the move off the surface goes along the axis")
    void bias() {
        World floor = new World();
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 30; z++) {
                floor.put(x, 4, z, 255);
            }
        }
        double[] normal = VoxelShadowMath.snapToAxis(0.1, 0.99, -0.05);
        assertEquals(1.0, normal[1], 0.0);
        double[] p = {10.5 + normal[0] * VoxelShadowMath.NORMAL_BIAS, 5.0 + normal[1] * VoxelShadowMath.NORMAL_BIAS,
                10.5 + normal[2] * VoxelShadowMath.NORMAL_BIAS};
        assertEquals(1.0, VoxelShadowMath.blockShadow(floor, p, new double[] {15.5, 12.0, 10.5}, 1, 0.0), 0.0);
        assertEquals(1.0, VoxelShadowMath.blockShadow(floor, p, new double[] {15.5, 12.0, 10.5}, 2, 0.25), 0.0);
        assertEquals(-1.0, VoxelShadowMath.snapToAxis(-0.9, 0.3, 0.3)[0], 0.0);
        assertEquals(-1.0, VoxelShadowMath.snapToAxis(0.3, -0.9, 0.3)[1], 0.0);
        assertEquals(1.0, VoxelShadowMath.snapToAxis(0.3, 0.3, 0.9)[2], 0.0);
    }

    @Test
    @DisplayName("two rays give a soft edge: a point at the edge of a wall's shadow is half lit, one clear of it fully")
    void softEdge() {
        World wall = new World();
        for (int y = 0; y < 20; y++) {
            for (int z = 0; z < 5; z++) {
                wall.put(10, y, z, 255);
            }
        }
        double[] light = {20.0, 5.5, 5.0};
        assertEquals(0.0, VoxelShadowMath.blockShadow(wall, new double[] {2.0, 5.5, 2.0}, light, 1, 0.25), 0.0);
        assertEquals(0.0, VoxelShadowMath.blockShadow(wall, new double[] {2.0, 5.5, 2.0}, new double[] {20.0, 5.5, 2.0}, 2, 0.25), 0.0);
        assertEquals(1.0, VoxelShadowMath.blockShadow(wall, new double[] {2.0, 5.5, 8.0}, new double[] {20.0, 5.5, 8.0}, 2, 0.25), 0.0);
        double edge = VoxelShadowMath.blockShadow(wall, new double[] {2.0, 5.5, 5.0}, light, 2, 0.25);
        assertEquals(0.5, edge, 1.0e-9);
        double single = VoxelShadowMath.blockShadow(wall, new double[] {2.0, 5.5, 5.0}, light, 1, 0.25);
        assertTrue(single == 0.0 || single == 1.0);
    }
}
