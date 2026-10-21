package com.synarsis.airtacticalarsenal.combat.hitbox;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Resolving a shot against the airframe")
class HitScanTest {

    private static Obb box(HitPart part, float cx, float cz, float half) {
        Obb obb = new Obb(part);
        obb.place(PartBox.of(part, half, half, half, cx, 0.0f, cz, 1.0f),
                0, 0, 0, new Quaternionf());
        return obb;
    }

    @Test
    @DisplayName("the nearest section along the shot wins, not the first in the list")
    void nearestSectionWins() {
        List<Obb> boxes = List.of(box(HitPart.ENGINE, 0, 4, 0.5f), box(HitPart.WARHEAD, 0, 1, 0.5f));

        HitScan.Hit hit = HitScan.nearest(boxes, new Vec3(0, 0, -2), new Vec3(0, 0, 6));

        assertNotNull(hit);
        assertEquals(HitPart.WARHEAD, hit.part());
        assertEquals(0.5, hit.point().z, 1.0E-6, "entry is the near face, not the centre");
    }

    @Test
    @DisplayName("a shot through a gap in the airframe hits nothing")
    void gapBetweenSectionsIsAMiss() {
        List<Obb> boxes = List.of(box(HitPart.WING_LEFT, 1.5f, 0, 0.5f),
                box(HitPart.WING_RIGHT, -1.5f, 0, 0.5f));

        assertNull(HitScan.nearest(boxes, new Vec3(0, 0, -3), new Vec3(0, 0, 3)),
                "straight down the gap between the wings must not register");
    }

    @Test
    @DisplayName("a shot that stops short of the airframe hits nothing")
    void shotStoppingShortIsAMiss() {
        List<Obb> boxes = List.of(box(HitPart.BODY, 0, 0, 0.5f));
        assertNull(HitScan.nearest(boxes, new Vec3(0, 0, -20), new Vec3(0, 0, -10)));
    }

    @Test
    @DisplayName("the entry point lies on the segment")
    void entryPointIsOnTheSegment() {
        List<Obb> boxes = List.of(box(HitPart.FUEL, 0, 0, 0.5f));
        Vec3 from = new Vec3(-3, 0, 0);
        Vec3 to = new Vec3(3, 0, 0);

        HitScan.Hit hit = HitScan.nearest(boxes, from, to);
        assertNotNull(hit);
        assertTrue(hit.t() >= 0.0 && hit.t() <= 1.0, "entry parameter outside the segment: " + hit.t());
        assertEquals(from.add(to.subtract(from).scale(hit.t())).x, hit.point().x, 1.0E-6);
    }

    @Test
    @DisplayName("a bare point resolves to the section containing it")
    void pointResolvesToItsSection() {
        List<Obb> boxes = List.of(box(HitPart.WING_LEFT, 1.5f, 0, 0.5f), box(HitPart.BODY, 0, 0, 0.4f));

        assertEquals(HitPart.WING_LEFT, HitScan.at(boxes, new Vec3(1.5, 0, 0)));
        assertEquals(HitPart.BODY, HitScan.at(boxes, new Vec3(0, 0, 0)));
        assertNull(HitScan.at(boxes, new Vec3(0.9, 0, 0)), "the gap belongs to no section");
    }

    @Test
    @DisplayName("the real Shahed table resolves a shot down the nose to the warhead")
    void realTableResolvesTheNose() {
        List<Obb> boxes = ShahedHitboxes.newBoxes();
        ShahedHitboxes.place(boxes, 0, 0, 0, 0, 0, 0);

        HitScan.Hit hit = HitScan.nearest(boxes, new Vec3(0, 0.19, 5), new Vec3(0, 0.19, 1.0));

        assertNotNull(hit, "a shot straight down the nose must land somewhere");
        assertEquals(HitPart.WARHEAD, hit.part());
    }

    @Test
    @DisplayName("the real Shahed table puts a shot through the wing on a wing")
    void realTableResolvesTheWing() {
        List<Obb> boxes = ShahedHitboxes.newBoxes();
        ShahedHitboxes.place(boxes, 0, 0, 0, 0, 0, 0);

        HitScan.Hit hit = HitScan.nearest(boxes, new Vec3(1.0, 3, -1.0), new Vec3(1.0, -3, -1.0));

        assertNotNull(hit, "a shot through the wing must land somewhere");
        assertEquals(HitPart.WING_LEFT, hit.part(),
                "local +X is the airframe's left; found " + hit.part().id());
    }
}
