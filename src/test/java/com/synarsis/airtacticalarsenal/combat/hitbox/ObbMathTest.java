package com.synarsis.airtacticalarsenal.combat.hitbox;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OBB: segment clipping and rotation")
class ObbMathTest {

    private static final PartBox UNIT =
            PartBox.of(HitPart.BODY, 0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 1.0f);

    private static Obb boxAt(double x, double y, double z, float yaw, float pitch, float roll) {
        Obb obb = new Obb(HitPart.BODY);
        obb.place(UNIT, x, y, z, ShahedHitboxes.bodyRotation(yaw, pitch, roll, new Quaternionf()));
        return obb;
    }

    @Test
    @DisplayName("a segment passing through scores a hit part-way along")
    void segmentThroughBoxHits() {
        Obb box = boxAt(0, 0, 0, 0, 0, 0);
        double t = box.clipSegment(new Vec3(-2, 0, 0), new Vec3(2, 0, 0));

        assertTrue(t > 0.0 && t < 1.0, "expected an entry parameter inside the segment, got " + t);
        assertEquals(0.375, t, 1.0E-6);
    }

    @Test
    @DisplayName("a segment that stops short of the box misses")
    void segmentStoppingShortMisses() {
        Obb box = boxAt(0, 0, 0, 0, 0, 0);
        double t = box.clipSegment(new Vec3(-20, 0, 0), new Vec3(-10, 0, 0));

        assertEquals(Obb.NO_HIT, t,
                "a segment ending short of the box must not register; got t=" + t);
    }

    @Test
    @DisplayName("the previous comparison accepted that same non-reaching segment")
    void previousComparisonAcceptedTheMiss() {
        Vec3 start = new Vec3(-20, 0, 0);
        Vec3 end = new Vec3(-10, 0, 0);
        AABB box = new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);

        assertTrue(legacyRayIntersectsAabb(start, end, box),
                "control failed: the old comparison was expected to accept this segment");
        assertEquals(Obb.NO_HIT, boxAt(0, 0, 0, 0, 0, 0).clipSegment(start, end),
                "the new clip must reject what the old comparison accepted");
    }

    private static boolean legacyRayIntersectsAabb(Vec3 start, Vec3 end, AABB box) {
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length < 0.001) {
            return box.contains(start);
        }
        double invDirX = Math.abs(dir.x) > 0.0001 ? 1.0 / dir.x : Double.MAX_VALUE;
        double invDirY = Math.abs(dir.y) > 0.0001 ? 1.0 / dir.y : Double.MAX_VALUE;
        double invDirZ = Math.abs(dir.z) > 0.0001 ? 1.0 / dir.z : Double.MAX_VALUE;

        double t1 = (box.minX - start.x) * invDirX;
        double t2 = (box.maxX - start.x) * invDirX;
        double t3 = (box.minY - start.y) * invDirY;
        double t4 = (box.maxY - start.y) * invDirY;
        double t5 = (box.minZ - start.z) * invDirZ;
        double t6 = (box.maxZ - start.z) * invDirZ;

        double tMin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        double tMax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        return tMax >= 0 && tMin <= tMax && tMin <= length;
    }

    @Test
    @DisplayName("a segment starting inside enters at zero")
    void segmentStartingInsideEntersAtZero() {
        Obb box = boxAt(0, 0, 0, 0, 0, 0);
        assertEquals(0.0, box.clipSegment(new Vec3(0, 0, 0), new Vec3(5, 0, 0)), 1.0E-9);
    }

    @Test
    @DisplayName("yawing the box by ninety degrees changes what a given segment hits")
    void rotationChangesWhatIsHit() {
        PartBox slab = PartBox.of(HitPart.BODY, 0.2f, 0.2f, 2.0f, 0.0f, 0.0f, 0.0f, 1.0f);

        Obb alongZ = new Obb(HitPart.BODY);
        alongZ.place(slab, 0, 0, 0, ShahedHitboxes.bodyRotation(0, 0, 0, new Quaternionf()));
        Obb alongX = new Obb(HitPart.BODY);
        alongX.place(slab, 0, 0, 0, ShahedHitboxes.bodyRotation(90, 0, 0, new Quaternionf()));

        Vec3 from = new Vec3(0, 0, 1.5);
        Vec3 to = new Vec3(0, 0, 1.0);

        assertTrue(alongZ.clipSegment(from, to) >= 0.0, "unrotated slab should reach out to z=1.5");
        assertEquals(Obb.NO_HIT, alongX.clipSegment(from, to),
                "once yawed to lie along X, the same segment must miss");
    }

    @Test
    @DisplayName("a full turn leaves the geometry where it started")
    void fullTurnIsIdentity() {
        Obb once = boxAt(3, 4, 5, 360, 0, 0);
        Obb never = boxAt(3, 4, 5, 0, 0, 0);

        Vec3 from = new Vec3(3, 4, 9);
        Vec3 to = new Vec3(3, 4, 1);
        assertEquals(never.clipSegment(from, to), once.clipSegment(from, to), 1.0E-5);
    }

    @Test
    @DisplayName("the axis-aligned hull contains every corner of the rotated box")
    void worldAabbContainsAllCorners() {
        Obb box = boxAt(10, 64, -7, 37, 21, 13);
        AABB hull = box.worldAabb();

        Quaternionf rot = box.rotation();
        Vector3f half = box.halfExtents();
        for (int i = 0; i < 8; i++) {
            Vector3f corner = rot.transform(
                    ((i & 1) == 0 ? -half.x : half.x),
                    ((i & 2) == 0 ? -half.y : half.y),
                    ((i & 4) == 0 ? -half.z : half.z),
                    new Vector3f());
            double cx = box.center().x + corner.x;
            double cy = box.center().y + corner.y;
            double cz = box.center().z + corner.z;

            assertFalse(cx < hull.minX - 1.0E-4 || cx > hull.maxX + 1.0E-4
                            || cy < hull.minY - 1.0E-4 || cy > hull.maxY + 1.0E-4
                            || cz < hull.minZ - 1.0E-4 || cz > hull.maxZ + 1.0E-4,
                    "corner " + i + " (" + cx + ", " + cy + ", " + cz + ") escaped " + hull);
        }
    }

    @Test
    @DisplayName("clipping stays exact far from the origin")
    void precisionHoldsAtWorldEdge() {
        double far = 2_000_000.0;
        Obb box = boxAt(far, 64, far, 0, 0, 0);

        double t = box.clipSegment(new Vec3(far - 2, 64, far), new Vec3(far + 2, 64, far));
        assertEquals(0.375, t, 1.0E-4, "entry parameter drifted far from spawn");
    }
}
