package com.synarsis.airtacticalarsenal.entity.tel.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OBB vs block box: separating axes")
class ObbAabbSatTest {

    private static Obb hull(float yaw) {
        return Obb.fromPose(0, 0, 0, yaw, 0, 0);
    }

    @Test
    @DisplayName("touching faces are not an overlap")
    void touchingIsNotOverlap() {
        AABB wall = new AABB(-3.95, 0.5, -1, -1.95, 2.5, 1);
        assertNull(ObbAabbSat.mtv(hull(0), wall));
    }

    @Test
    @DisplayName("a box inside the hull overlaps")
    void insideOverlaps() {
        AABB inside = new AABB(-0.3, 1.0, -0.3, 0.3, 1.6, 0.3);
        assertNotNull(ObbAabbSat.mtv(hull(0), inside));
    }

    @Test
    @DisplayName("a wall 0.3 into the left side pushes the hull 0.3 the other way")
    void pushIsMinimalAndAwayFromTheBox() {
        AABB wall = new AABB(1.65, 0.5, -1, 3.65, 2.5, 1);
        Vec3 push = ObbAabbSat.mtv(hull(0), wall);
        assertNotNull(push);
        assertEquals(-0.3, push.x, 1.0E-9);
        assertEquals(0.0, push.y, 1.0E-9);
        assertEquals(0.0, push.z, 1.0E-9);
    }

    @Test
    @DisplayName("ground under the hull pushes it up")
    void groundPushesUp() {
        AABB ground = new AABB(-10, -1, -10, 10, 0.05, 10);
        Vec3 push = ObbAabbSat.mtv(hull(0), ground);
        assertNotNull(push);
        assertEquals(0.05, push.y, 1.0E-9);
    }

    @Test
    @DisplayName("a turned hull misses a block in the corner of its bounding square")
    void turnedHullLeavesTheCorner() {
        Obb turned = hull(45f);
        AABB bounds = turned.worldAabb();
        AABB corner = new AABB(bounds.maxX - 1.0, 0.5, bounds.maxZ - 1.0, bounds.maxX, 1.5, bounds.maxZ);
        assertTrue(bounds.intersects(corner), "the block must be inside the bounding square for this to mean anything");
        assertNull(ObbAabbSat.mtv(turned, corner));
    }

    @Test
    @DisplayName("a turned hull is pushed away from a block it overlaps at an edge")
    void pushPointsAwayFromTheBlock() {
        Obb turned = hull(30f);
        Vec3 c = turned.centre();
        Vec3 side = turned.point(-1.8, 0.0, 0.0);
        AABB block = new AABB(side.x - 0.5, 0.5, side.z - 0.5, side.x + 0.5, 1.5, side.z + 0.5);
        Vec3 push = ObbAabbSat.mtv(turned, block);
        assertNotNull(push);
        Vec3 toBlock = block.getCenter().subtract(c);
        assertTrue(push.dot(toBlock) < 0.0, "push must lead away from the block, was " + push);
    }
}
