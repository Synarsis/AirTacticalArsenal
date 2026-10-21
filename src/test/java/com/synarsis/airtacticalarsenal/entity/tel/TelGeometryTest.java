package com.synarsis.airtacticalarsenal.entity.tel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TEL geometry: local to world")
class TelGeometryTest {

    private static final double EPS = 1.0E-9;
    private static final float[] YAWS = {0f, 90f, 180f, 270f};

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS, "x of " + actual);
        assertEquals(expected.y, actual.y, EPS, "y of " + actual);
        assertEquals(expected.z, actual.z, EPS, "z of " + actual);
    }

    @Test
    @DisplayName("+Z of the vehicle is the vanilla look vector at every yaw")
    void forwardIsLookVector() {
        for (float yaw : YAWS) {
            Vec3 world = TelGeometry.localToWorld(0, 0, 0, yaw, 0, 0, new Vec3(0, 0, 1));
            assertVec(Vec3.directionFromRotation(0, yaw), world);
        }
    }

    @Test
    @DisplayName("+X of the vehicle is the left-hand side of the look vector")
    void leftIsPlusX() {
        for (float yaw : YAWS) {
            Vec3 look = Vec3.directionFromRotation(0, yaw);
            Vec3 expectedLeft = new Vec3(look.z, 0, -look.x);
            Vec3 world = TelGeometry.localToWorld(0, 0, 0, yaw, 0, 0, new Vec3(1, 0, 0));
            assertVec(expectedLeft, world);
        }
    }

    @Test
    @DisplayName("the position is added after the turn")
    void positionIsTranslation() {
        Vec3 world = TelGeometry.localToWorld(10, 64, -5, 90f, 0, 0, new Vec3(0, 2, 3));
        assertVec(new Vec3(10 - 3, 66, -5), world);
    }

    @Test
    @DisplayName("positive pitch drops the nose")
    void pitchLowersNose() {
        Vec3 nose = new Vec3(0, 0, 5);
        Vec3 level = TelGeometry.localToWorld(0, 0, 0, 0, 0, 0, nose);
        Vec3 pitched = TelGeometry.localToWorld(0, 0, 0, 0, 10f, 0, nose);
        assertTrue(pitched.y < level.y, "nose should be lower, was " + pitched.y);

        Vec3 tail = TelGeometry.localToWorld(0, 0, 0, 0, 10f, 0, new Vec3(0, 0, -5));
        assertTrue(tail.y > level.y, "tail should be higher, was " + tail.y);
    }

    @Test
    @DisplayName("positive roll lifts the left side")
    void rollLiftsLeft() {
        Vec3 left = new Vec3(2, 0, 0);
        Vec3 rolled = TelGeometry.localToWorld(0, 0, 0, 0, 0, 10f, left);
        assertTrue(rolled.y > 0, "left side should be higher, was " + rolled.y);

        Vec3 right = TelGeometry.localToWorld(0, 0, 0, 0, 0, 10f, new Vec3(-2, 0, 0));
        assertTrue(right.y < 0, "right side should be lower, was " + right.y);
    }

    @Test
    @DisplayName("the pivot point does not move however the hull is tilted")
    void pivotIsFixed() {
        Vec3 pivot = new Vec3(0, TelGeometry.PIVOT_H, 0);
        Vec3 world = TelGeometry.localToWorld(3, 70, 4, 33f, 17f, -12f, pivot);
        assertVec(new Vec3(3, 70 + TelGeometry.PIVOT_H, 4), world);
    }

    @Test
    @DisplayName("file coordinates flip X")
    void fileToLocalFlipsX() {
        assertVec(new Vec3(-0.65625, 1.78125, -5.78125), TelGeometry.fileToLocal(10.5, 28.5, -92.5));
    }

    @Test
    @DisplayName("missile bases mirror each other across the centre line")
    void missileBasesMirror() {
        assertEquals(TelGeometry.MISSILE_BASE[0].x, -TelGeometry.MISSILE_BASE[1].x, EPS);
        assertEquals(TelGeometry.MISSILE_BASE[0].y, TelGeometry.MISSILE_BASE[1].y, EPS);
        assertEquals(TelGeometry.MISSILE_BASE[0].z, TelGeometry.MISSILE_BASE[1].z, EPS);
    }

    @Test
    @DisplayName("the oriented box overlaps what is inside it and misses what is beside it")
    void obbOverlap() {
        AABB inside = new AABB(-0.5, 0.5, -0.5, 0.5, 1.5, 0.5);
        assertTrue(TelGeometry.obbIntersectsAabb(0, 0, 0, 0, 0, 0, inside, 0.0));

        AABB beside = new AABB(3.0, 0.5, -0.5, 4.0, 1.5, 0.5);
        assertFalse(TelGeometry.obbIntersectsAabb(0, 0, 0, 0, 0, 0, beside, 0.0));

        assertTrue(TelGeometry.obbIntersectsAabb(0, 0, 0, 90f, 0, 0, beside, 0.0));
    }

    @Test
    @DisplayName("shrink lets a box that only touches pass")
    void obbShrinkIgnoresTouching() {
        AABB touching = new AABB(1.95, 0.5, -0.5, 3.0, 1.5, 0.5);
        assertFalse(TelGeometry.obbIntersectsAabb(0, 0, 0, 0, 0, 0, touching, 0.01));
    }
}
