package com.synarsis.airtacticalarsenal.entity.tel.physics;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Wall hull sweep: sideways only")
class ObbSweepTest {

    private static final ObbSweep.Params P = ObbSweep.Params.STANDARD;
    private static final double NOSE = 7.81;

    private static Obb hull(float yaw) {
        return Obb.wallHull(0, 0, 0, yaw, 0, 0);
    }

    private static AABB wall(double z0, double height) {
        return new AABB(-8, 0, z0, 8, height, z0 + 1.0);
    }

    @Test
    @DisplayName("driving at a wall stops at its face")
    void stopsAtTheWall() {
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 0, 3.0, List.of(wall(10.0, 3.0)), P);
        assertEquals(10.0 - NOSE, r.dz(), 0.01);
        assertTrue(r.shortZ() > 0.5);
        assertEquals(0.0, r.shortX(), 0.0);
    }

    @Test
    @DisplayName("it says which block stopped it")
    void namesTheBlocker() {
        AABB w = wall(10.0, 3.0);
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 0, 3.0, List.of(w), P);
        assertNotNull(r.blockerZ());
        assertEquals(w, r.blockerZ());
    }

    @Test
    @DisplayName("only the part of the move into the wall is lost; the rest slides")
    void slidesAlongTheWall() {
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 1.0, 3.0, List.of(wall(10.0, 3.0)), P);
        assertEquals(1.0, r.dx(), 1.0E-9);
        assertEquals(10.0 - NOSE, r.dz(), 0.01);
    }

    @Test
    @DisplayName("a block 1.3 high, a terrace, is under the hull and does not stop it")
    void lowThingsAreTheSuspensionsBusiness() {
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 0, 3.0, List.of(wall(9.0, 1.3)), P);
        assertEquals(3.0, r.dz(), 1.0E-9);
        assertEquals(0.0, r.shortZ(), 0.0);
    }

    @Test
    @DisplayName("a block 1.6 high is a wall")
    void higherIsAWall() {
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 0, 3.0, List.of(wall(9.0, 1.6)), P);
        assertEquals(9.0 - NOSE, r.dz(), 0.01);
    }

    @Test
    @DisplayName("a move never reverses, and leaving a wall is never held back")
    void canAlwaysDriveOut() {
        List<AABB> boxes = List.of(wall(7.61, 3.0));
        ObbSweep.Result into = ObbSweep.resolve(hull(0), 0, 0.5, boxes, P);
        assertTrue(into.dz() >= 0.0 && into.dz() < 1.0E-6, "forward into the wall gives nothing, was " + into.dz());
        ObbSweep.Result away = ObbSweep.resolve(hull(0), 0, -0.5, boxes, P);
        assertEquals(-0.5, away.dz(), 1.0E-9);
        ObbSweep.Result along = ObbSweep.resolve(hull(0), 0.5, 0, boxes, P);
        assertEquals(0.5, along.dx(), 1.0E-9);
    }

    @Test
    @DisplayName("a turned hull is stopped by its corner, not by its bounding square")
    void cornerStopsTheTurnedHull() {
        Obb turned = hull(45f);
        double reach = turned.worldAabb().maxZ;
        ObbSweep.Result r = ObbSweep.resolve(turned, 0, 4.0, List.of(wall(10.0, 3.0)), P);
        assertEquals(10.0 - reach, r.dz(), 0.05);
        assertTrue(r.shortZ() > 0.0);
    }

    @Test
    @DisplayName("a thin post is not driven through")
    void thinPost() {
        AABB post = new AABB(-0.1, 0, 8.5, 0.1, 2.0, 8.7);
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 0, 2.0, List.of(post), P);
        assertEquals(8.5 - NOSE, r.dz(), 0.05);
    }

    @Test
    @DisplayName("height is untouched: ground boxes under the hull never matter")
    void groundIsIgnored() {
        AABB ground = new AABB(-30, -1, -30, 30, 0.9, 30);
        ObbSweep.Result r = ObbSweep.resolve(hull(0), 1.0, 1.0, List.of(ground), P);
        assertEquals(1.0, r.dx(), 1.0E-9);
        assertEquals(1.0, r.dz(), 1.0E-9);
    }

    @Test
    @DisplayName("intersects spots real overlap and shrugs off a graze")
    void intersectsHasATolerance() {
        Obb h = hull(0);
        assertTrue(ObbSweep.intersects(h, List.of(wall(5.0, 3.0)), 0.1));
        assertFalse(ObbSweep.intersects(h, List.of(wall(NOSE - 0.05, 3.0)), 0.1));
        assertFalse(ObbSweep.intersects(h, List.of(wall(20.0, 3.0)), 0.1));
    }

    private static final AABB TAIL_WALL = new AABB(1.5, 0, -9.0, 6.0, 3.0, -5.0);

    @Test
    @DisplayName("a turn that swings the tail into a wall is refused")
    void tailDoesNotSwingIntoTheWall() {
        AABB clear = new AABB(2.4, 0, -9.0, 8.0, 3.0, -5.0);
        Obb before = hull(0);
        assertEquals(0.0, ObbSweep.depth(before, List.of(clear), P), 0.0);
        boolean refused = false;
        for (float yaw : new float[] {-8f, 8f}) {
            Obb after = hull(yaw);
            if (ObbSweep.depth(after, List.of(clear), P) > 0.1) {
                assertFalse(ObbSweep.turnAllowed(before, after, List.of(clear), P, 0.1));
                refused = true;
            }
        }
        assertTrue(refused, "one of the two turns should reach the wall");
    }

    @Test
    @DisplayName("a turn that backs the tail out of a wall is allowed, so a pinned launcher can be steered free")
    void turningAwayIsAllowed() {
        Obb before = hull(0);
        double now = ObbSweep.depth(before, List.of(TAIL_WALL), P);
        assertTrue(now > 0.1, "the setup should start with the tail in the wall, depth " + now);

        boolean someTurnFrees = false;
        boolean someTurnDigsIn = false;
        for (float yaw : new float[] {-5f, 5f}) {
            Obb after = hull(yaw);
            double then = ObbSweep.depth(after, List.of(TAIL_WALL), P);
            boolean allowed = ObbSweep.turnAllowed(before, after, List.of(TAIL_WALL), P, 0.1);
            if (then < now) {
                assertTrue(allowed, "a turn that gets out must be allowed");
                someTurnFrees = true;
            } else {
                assertFalse(allowed, "a turn that digs in must be refused");
                someTurnDigsIn = true;
            }
        }
        assertTrue(someTurnFrees && someTurnDigsIn);
    }

    @Test
    @DisplayName("a small touch under the limit never stops a turn")
    void smallTouchIsFine() {
        AABB graze = new AABB(1.9, 0, -9.0, 6.0, 3.0, -5.0);
        for (float yaw : new float[] {-3f, 3f}) {
            Obb after = hull(yaw);
            if (ObbSweep.depth(after, List.of(graze), P) <= 0.1) {
                assertTrue(ObbSweep.turnAllowed(hull(0), after, List.of(graze), P, 0.1));
            }
        }
    }
}
