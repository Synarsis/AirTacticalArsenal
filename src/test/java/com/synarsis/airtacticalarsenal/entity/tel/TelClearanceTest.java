package com.synarsis.airtacticalarsenal.entity.tel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Room round the launcher")
class TelClearanceTest {

    private static final TelClearance.Pose HERE = new TelClearance.Pose(0.0, 0.0, 0.0, 0f, 0f, 0f);
    private static final int CLEARANCE = 40;

    private static TelClearance.Solid world(BlockPos... blocks) {
        Set<BlockPos> set = new HashSet<>(Set.of(blocks));
        return (x, y, z) -> set.contains(new BlockPos(x, y, z));
    }

    private static BlockPos at(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    @Test
    @DisplayName("an empty world is clear everywhere")
    void empty() {
        TelClearance.Solid nothing = world();
        assertNull(TelClearance.roofBlock(HERE, nothing));
        for (int slot = 0; slot < 2; slot++) {
            assertNull(TelClearance.arcBlock(slot, HERE, nothing));
            assertNull(TelClearance.skyBlock(slot, HERE, CLEARANCE, nothing));
        }
    }

    @Test
    @DisplayName("a block on the roof of the bay, over a round's foot, is in the way of the lift and of the sky")
    void blockOnTheRoof() {
        BlockPos block = at(0.66, 4.0, -5.8);
        TelClearance.Solid w = world(block);
        BlockPos arc = TelClearance.arcBlock(0, HERE, w);
        BlockPos sky = TelClearance.skyBlock(0, HERE, CLEARANCE, w);
        assertNotNull(arc, "the round would hit it as it stood up");
        assertNotNull(sky, "and the sky over it is not clear");
        assertEquals(block, arc);
        assertEquals(block, sky);
        assertNull(TelClearance.roofBlock(HERE, w), "but the covers fold out clear of it");
    }

    @Test
    @DisplayName("a block beside the bay is in the way of the covers and nothing else")
    void blockBeside() {
        BlockPos block = at(3.0, 3.5, -1.0);
        TelClearance.Solid w = world(block);
        assertEquals(block, TelClearance.roofBlock(HERE, w));
        for (int slot = 0; slot < 2; slot++) {
            assertNull(TelClearance.arcBlock(slot, HERE, w));
            assertNull(TelClearance.skyBlock(slot, HERE, CLEARANCE, w));
        }
        assertNotNull(TelClearance.roofBlock(HERE, world(at(-3.0, 3.5, -1.0))));
    }

    @Test
    @DisplayName("a block thirty blocks over the round blocks the sky, and one fifty over does not")
    void blockHighUp() {
        BlockPos near = at(0.66, 30.0, -5.78);
        assertEquals(near, TelClearance.skyBlock(0, HERE, CLEARANCE, world(near)));
        assertNull(TelClearance.arcBlock(0, HERE, world(near)));
        BlockPos far = at(0.66, 55.0, -5.78);
        assertNull(TelClearance.skyBlock(0, HERE, CLEARANCE, world(far)));
        assertEquals(far, TelClearance.skyBlock(0, HERE, 60, world(far)));
    }

    @Test
    @DisplayName("a block well to one side blocks nothing")
    void blockAside() {
        TelClearance.Solid w = world(at(8.0, 5.0, -5.8), at(-8.0, 5.0, 0.0), at(0.0, 5.0, 12.0));
        assertNull(TelClearance.roofBlock(HERE, w));
        for (int slot = 0; slot < 2; slot++) {
            assertNull(TelClearance.arcBlock(slot, HERE, w));
            assertNull(TelClearance.skyBlock(slot, HERE, CLEARANCE, w));
        }
    }

    @Test
    @DisplayName("what the world counts as solid is the world's business: blocks it calls soft are not in the way")
    void softBlocksAreNotSolid() {
        TelClearance.Solid grassEverywhere = (x, y, z) -> false;
        assertNull(TelClearance.roofBlock(HERE, grassEverywhere));
        assertNull(TelClearance.arcBlock(0, HERE, grassEverywhere));
        assertNull(TelClearance.arcBlock(1, HERE, grassEverywhere));
        assertNull(TelClearance.skyBlock(0, HERE, CLEARANCE, grassEverywhere));
        assertNull(TelClearance.skyBlock(1, HERE, CLEARANCE, grassEverywhere));
    }

    @Test
    @DisplayName("the lift of each round sweeps its own side of the bay")
    void slotsAreMirrored() {
        BlockPos left = at(TelGeometry.MISSILE_BASE[0].x, 5.0, -5.8);
        BlockPos right = at(TelGeometry.MISSILE_BASE[1].x, 5.0, -5.8);
        assertNotNull(TelClearance.arcBlock(0, HERE, world(left)));
        assertNotNull(TelClearance.arcBlock(1, HERE, world(right)));
        assertNull(TelClearance.arcBlock(0, HERE, world(at(2.5, 5.0, -5.8))));
    }

    @Test
    @DisplayName("the nearest blocking block is the one reported")
    void nearestIsReported() {
        BlockPos close = at(0.66, 4.0, -5.8);
        BlockPos farther = at(0.66, 8.0, -5.8);
        assertEquals(close, TelClearance.arcBlock(0, HERE, world(farther, close)));
    }

    @Test
    @DisplayName("the cells follow the machine's heading")
    void followsHeading() {
        TelClearance.Pose turned = new TelClearance.Pose(0.0, 0.0, 0.0, 90f, 0f, 0f);
        Set<BlockPos> cells = TelClearance.blocks(TelClearance.roofCells(), turned);
        Vec3 sample = turned.toWorld(new Vec3(2.5, 3.5, 0.0));
        assertTrue(cells.contains(new BlockPos((int) Math.floor(sample.x), (int) Math.floor(sample.y),
                (int) Math.floor(sample.z))));
        assertFalse(cells.contains(at(2.5, 3.5, 0.0)));
    }

    @Test
    @DisplayName("the cell sets are a workable size")
    void sizes() {
        assertTrue(TelClearance.roofCells().size() < 1500, "roof " + TelClearance.roofCells().size());
        assertTrue(TelClearance.arcCells(0).size() < 6000, "arc " + TelClearance.arcCells(0).size());
        assertTrue(TelClearance.arcCells(0).size() > 100);
        assertTrue(TelClearance.blocks(TelClearance.arcCells(0), HERE).size() < 700);
        assertTrue(TelClearance.skyBlocks(0, HERE, CLEARANCE).size() > 9 * CLEARANCE);
    }

    @Test
    @DisplayName("a message names the block as three numbers")
    void describe() {
        assertEquals("12 64 -30", TelClearance.describe(new BlockPos(12, 64, -30)));
        assertEquals("", TelClearance.describe(null));
    }

    @Test
    @DisplayName("the debug outlines draw each zone as thin lines inside the zone, and nothing for free cells")
    void outlines() {
        assertEquals(24, TelClearance.roofOutline().size(), "two boxes of twelve edges");
        for (int slot = 0; slot < 2; slot++) {
            assertEquals(12, TelClearance.skyOutline(slot).size());
            assertEquals(11, TelClearance.arcOutline(slot).size(), "nine chords of the arc and the two radii");
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (Vec3[] seg : TelClearance.roofOutline()) {
            for (Vec3 p : seg) {
                minX = Math.min(minX, Math.abs(p.x));
                maxX = Math.max(maxX, Math.abs(p.x));
            }
        }
        assertEquals(1.7, minX, 1.0E-9);
        assertEquals(3.3, maxX, 1.0E-9);
        double reach = 0.0;
        for (Vec3[] seg : TelClearance.arcOutline(0)) {
            reach = Math.max(reach, Math.max(seg[0].z, seg[1].z));
        }
        double farthestCell = TelClearance.arcCells(0).stream().mapToDouble(v -> v.z).max().orElse(0);
        assertTrue(reach >= farthestCell - 1.0E-9, "outline " + reach + " cells " + farthestCell);
    }
}
