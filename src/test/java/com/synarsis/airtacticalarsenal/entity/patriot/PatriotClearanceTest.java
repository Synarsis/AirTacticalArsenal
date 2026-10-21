package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelClearance;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot place checks on a synthetic world")
class PatriotClearanceTest {

    private static final TelClearance.Pose AT = new TelClearance.Pose(0.5, 0.0, 0.5, 0.0f, 0.0f, 0.0f);

    private static final class World implements TelClearance.Solid {
        final Set<BlockPos> added = new HashSet<>();
        final Set<BlockPos> removed = new HashSet<>();
        Predicate<BlockPos> hole = p -> false;

        @Override
        public boolean at(int x, int y, int z) {
            BlockPos p = new BlockPos(x, y, z);
            if (this.added.contains(p)) {
                return true;
            }
            if (this.removed.contains(p) || this.hole.test(p)) {
                return false;
            }
            return y < 0;
        }
    }

    private static PatriotHitbox model(PatriotKind kind) {
        return PatriotHitbox.of(kind);
    }

    @Test
    @DisplayName("on flat ground nothing is in the way of the supports, the lift or the tractor")
    void flatGroundIsFree() {
        for (PatriotKind kind : PatriotKind.values()) {
            PatriotHitbox m = model(kind);
            World world = new World();
            assertNull(PatriotClearance.supportBlocker(m, PatriotPose.REST, AT, world), kind + " supports");
            assertNull(PatriotClearance.liftBlocker(m, PatriotPose.REST, AT, world), kind + " lift");
            assertNull(PatriotClearance.tractorBlocker(m, PatriotPose.REST, AT, world), kind + " tractor");
        }
    }

    @Test
    @DisplayName("a foot over a pit has nothing to stand on")
    void footOverPit() {
        for (PatriotKind kind : PatriotKind.values()) {
            PatriotHitbox m = model(kind);
            Set<BlockPos> feet = PatriotClearance.supportCells(m, PatriotPose.REST, AT);
            assertFalse(feet.isEmpty());
            BlockPos foot = feet.iterator().next();
            World world = new World();
            world.hole = p -> p.getX() == foot.getX() && p.getZ() == foot.getZ() && p.getY() >= -4;
            PatriotClearance.Blocker blocker = PatriotClearance.supportBlocker(m, PatriotPose.REST, AT, world);
            assertNotNull(blocker, kind + " pit under " + foot);
            assertEquals(PatriotRefusal.SUPPORTS_NO_GROUND, blocker.reason());
        }
    }

    @Test
    @DisplayName("a block where the foot comes down refuses the supports")
    void footInsideBlock() {
        PatriotHitbox m = model(PatriotKind.RADAR);
        BlockPos foot = PatriotClearance.supportCells(m, PatriotPose.REST, AT).iterator().next();
        World world = new World();
        world.added.add(new BlockPos(foot.getX(), foot.getY() + 1, foot.getZ()));
        world.added.add(foot);
        PatriotClearance.Blocker blocker = PatriotClearance.supportBlocker(m, PatriotPose.REST, AT, world);
        assertNotNull(blocker);
        assertEquals(PatriotRefusal.SUPPORTS_NO_GROUND, blocker.reason());
    }

    @Test
    @DisplayName("a block anywhere on the way of the raised pack or antenna stops the lift, and the cell is named")
    void blockOverTheLift() {
        for (PatriotKind kind : PatriotKind.values()) {
            PatriotHitbox m = model(kind);
            Set<BlockPos> cells = PatriotClearance.liftCells(m, PatriotPose.REST, AT);
            BlockPos highest = null;
            for (BlockPos c : cells) {
                if (highest == null || c.getY() > highest.getY()) {
                    highest = c;
                }
            }
            assertNotNull(highest);
            assertTrue(highest.getY() >= 4, kind + " the lift reaches up to " + highest);
            World world = new World();
            world.added.add(highest);
            PatriotClearance.Blocker blocker = PatriotClearance.liftBlocker(m, PatriotPose.REST, AT, world);
            assertNotNull(blocker, kind.toString());
            assertEquals(PatriotRefusal.LIFT_BLOCKED, blocker.reason());
            assertEquals(highest, blocker.at());
        }
    }

    @Test
    @DisplayName("a block that only the middle of the lift reaches is found as well")
    void blockInTheMiddleOfTheArc() {
        PatriotHitbox m = model(PatriotKind.LAUNCHER);
        Set<BlockPos> all = PatriotClearance.liftCells(m, PatriotPose.REST, AT);
        Set<BlockPos> parkedOnly = PatriotClearance.cellsOf(m.compute(PatriotPose.REST, 0.5, 0.0, 0.5, 0f, 0f, 0f),
                PatriotClearance.CELL_SHRINK);
        BlockPos pick = null;
        for (BlockPos c : all) {
            if (!parkedOnly.contains(c) && (pick == null || c.getY() > pick.getY())) {
                pick = c;
            }
        }
        assertNotNull(pick);
        World world = new World();
        world.added.add(pick);
        assertNotNull(PatriotClearance.liftBlocker(m, PatriotPose.REST, AT, world));
    }

    @Test
    @DisplayName("a wall in front of the tractor, or a ditch under the places its wheels reach, refuses the detach")
    void tractorPath() {
        for (PatriotKind kind : PatriotKind.values()) {
            PatriotHitbox m = model(kind);
            Set<BlockPos> path = PatriotClearance.tractorCells(m, PatriotPose.REST, AT);
            BlockPos farthest = null;
            for (BlockPos c : path) {
                if (c.getY() >= 1 && (farthest == null || c.getZ() > farthest.getZ())) {
                    farthest = c;
                }
            }
            assertNotNull(farthest);
            assertTrue(farthest.getZ() >= 20, kind + " the tractor reaches z " + farthest);
            World wall = new World();
            wall.added.add(farthest);
            PatriotClearance.Blocker blocked = PatriotClearance.tractorBlocker(m, PatriotPose.REST, AT, wall);
            assertNotNull(blocked, kind.toString());
            assertEquals(PatriotRefusal.TRACTOR_NO_ROOM, blocked.reason());

            World ditch = new World();
            ditch.hole = p -> p.getZ() >= 15 && p.getY() >= -5;
            assertNotNull(PatriotClearance.tractorBlocker(m, PatriotPose.REST, AT, ditch), kind + " ditch");
        }
    }

    @Test
    @DisplayName("blocks the solidity test says are not solid do not count")
    void softBlocksAreIgnored() {
        PatriotHitbox m = model(PatriotKind.LAUNCHER);
        Set<BlockPos> cells = PatriotClearance.liftCells(m, PatriotPose.REST, AT);
        World world = new World();
        world.added.addAll(cells);
        TelClearance.Solid ignoring = (x, y, z) -> y < 0;
        assertNotNull(PatriotClearance.liftBlocker(m, PatriotPose.REST, AT, world));
        assertNull(PatriotClearance.liftBlocker(m, PatriotPose.REST, AT, ignoring));
    }

    @Test
    @DisplayName("the checks follow the vehicle when it is turned")
    void turned() {
        PatriotHitbox m = model(PatriotKind.RADAR);
        TelClearance.Pose turned = new TelClearance.Pose(0.5, 0.0, 0.5, 90.0f, 0.0f, 0.0f);
        Set<BlockPos> straight = PatriotClearance.tractorCells(m, PatriotPose.REST, AT);
        Set<BlockPos> sideways = PatriotClearance.tractorCells(m, PatriotPose.REST, turned);
        int maxZ = straight.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        int minX = sideways.stream().mapToInt(BlockPos::getX).min().orElse(0);
        assertTrue(maxZ > 15);
        assertTrue(minX < -15, "turned by 90 degrees the tractor points to -X: " + minX);
    }

    @Test
    @DisplayName("tilt limits")
    void tilt() {
        assertTrue(PatriotClearance.tiltOk(5.9f, -5.9f));
        assertFalse(PatriotClearance.tiltOk(6.1f, 0.0f));
        assertFalse(PatriotClearance.tiltOk(0.0f, -6.1f));
    }
}
