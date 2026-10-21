package com.synarsis.airtacticalarsenal.combat.hitbox;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Shahed hitboxes: template and body rotation")
class ShahedHitboxTemplateTest {

    private static final float MODEL_MIN_X = -1.450f;
    private static final float MODEL_MAX_X = 1.4583f;
    private static final float MODEL_MIN_Y = -0.126f;
    private static final float MODEL_MAX_Y = 0.5004f;
    private static final float MODEL_MIN_Z = -1.9856f;
    private static final float MODEL_MAX_Z = 1.638f;

    private static Vector3f rotate(float yaw, float pitch, float roll, float x, float y, float z) {
        return ShahedHitboxes.bodyRotation(yaw, pitch, roll, new Quaternionf())
                .transform(x, y, z, new Vector3f());
    }

    @Test
    @DisplayName("durability shares add up to the whole airframe")
    void hpFractionsSumToOne() {
        float total = 0.0f;
        for (PartBox box : ShahedHitboxes.TEMPLATE) {
            total += box.hpFraction();
        }
        assertEquals(1.0f, total, 1.0E-4f);
    }

    @Test
    @DisplayName("every part is covered and no box is flat")
    void tableIsWellFormed() {
        for (PartBox box : ShahedHitboxes.TEMPLATE) {
            assertTrue(box.halfExtents().x() > 0 && box.halfExtents().y() > 0 && box.halfExtents().z() > 0,
                    box.part().id() + " has a zero-volume box");
            assertTrue(box.hpFraction() > 0, box.part().id() + " carries no durability");
        }
        for (HitPart part : HitPart.values()) {
            assertTrue(ShahedHitboxes.TEMPLATE.stream().anyMatch(b -> b.part() == part),
                    part.id() + " has no box, so nothing can ever hit it");
        }
    }

    @Test
    @DisplayName("no box sticks out past the model it is tracing")
    void boxesStayInsideTheModel() {
        Vector3f ext = new Vector3f();
        for (PartBox box : ShahedHitboxes.TEMPLATE) {
            Vector3f c = box.center();
            box.rotatedExtents(ext);
            assertTrue(c.x() - ext.x >= MODEL_MIN_X - 0.02f && c.x() + ext.x <= MODEL_MAX_X + 0.02f,
                    box.part().id() + " overhangs the model on X");
            assertTrue(c.y() - ext.y >= MODEL_MIN_Y - 0.02f && c.y() + ext.y <= MODEL_MAX_Y + 0.02f,
                    box.part().id() + " overhangs the model on Y");
            assertTrue(c.z() - ext.z >= MODEL_MIN_Z - 0.02f && c.z() + ext.z <= MODEL_MAX_Z + 0.02f,
                    box.part().id() + " overhangs the model on Z");
        }
    }

    @Test
    @DisplayName("the hull encloses every part box")
    void hullEnclosesTheTable() {
        Vector3f hc = ShahedHitboxes.HULL.center();
        Vector3f hh = ShahedHitboxes.HULL.halfExtents();
        Vector3f ext = new Vector3f();
        for (PartBox box : ShahedHitboxes.TEMPLATE) {
            Vector3f c = box.center();
            box.rotatedExtents(ext);
            assertTrue(c.x() - ext.x >= hc.x() - hh.x() - 1.0E-3f && c.x() + ext.x <= hc.x() + hh.x() + 1.0E-3f,
                    box.part().id() + " escapes the hull on X");
            assertTrue(c.y() - ext.y >= hc.y() - hh.y() - 1.0E-3f && c.y() + ext.y <= hc.y() + hh.y() + 1.0E-3f,
                    box.part().id() + " escapes the hull on Y");
            assertTrue(c.z() - ext.z >= hc.z() - hh.z() - 1.0E-3f && c.z() + ext.z <= hc.z() + hh.z() + 1.0E-3f,
                    box.part().id() + " escapes the hull on Z");
        }
    }

    @Test
    @DisplayName("with no rotation the nose points along world +Z")
    void levelFlightPointsAlongPositiveZ() {
        Vector3f nose = rotate(0, 0, 0, 0, 0, 1);
        assertEquals(0.0f, nose.x(), 1.0E-5f);
        assertEquals(0.0f, nose.y(), 1.0E-5f);
        assertEquals(1.0f, nose.z(), 1.0E-5f);
    }

    @Test
    @DisplayName("yaw ninety puts the nose along world +X, matching atan2(motion.x, motion.z)")
    void yawNinetyPointsAlongPositiveX() {
        Vector3f nose = rotate(90, 0, 0, 0, 0, 1);
        assertEquals(1.0f, nose.x(), 1.0E-5f);
        assertEquals(0.0f, nose.z(), 1.0E-5f);
    }

    @Test
    @DisplayName("positive pitch raises the nose")
    void positivePitchRaisesTheNose() {
        Vector3f nose = rotate(0, 45, 0, 0, 0, 1);
        assertTrue(nose.y() > 0.6f,
                "pitch is stored positive nose-up, so the rotation must lift local +Z; got y=" + nose.y());
    }

    @Test
    @DisplayName("positive roll drops one wing and lifts the other")
    void rollTiltsTheWings() {
        Vector3f right = rotate(0, 0, 30, 1, 0, 0);
        Vector3f left = rotate(0, 0, 30, -1, 0, 0);
        assertTrue(Math.abs(right.y()) > 0.4f, "roll did not tilt the wings at all");
        assertEquals(-right.y(), left.y(), 1.0E-5f, "the wings must tilt in opposite directions");
    }

    @Test
    @DisplayName("wing boxes stay on their own side of the fuselage")
    void wingBoxesStayOnTheirSide() {
        for (PartBox box : ShahedHitboxes.TEMPLATE) {
            if (box.part() == HitPart.WING_LEFT) {
                assertTrue(box.center().x() > 0.0f, "a WING_LEFT box strayed to starboard");
            } else if (box.part() == HitPart.WING_RIGHT) {
                assertTrue(box.center().x() < 0.0f, "a WING_RIGHT box strayed to port");
            }
        }
    }

    @Test
    @DisplayName("heading south, the left wing sits to the east")
    void leftWingIsOnTheAirframesLeft() {
        PartBox left = ShahedHitboxes.TEMPLATE.stream()
                .filter(b -> b.part() == HitPart.WING_LEFT)
                .max(java.util.Comparator.comparing(b -> b.halfExtents().x())).orElseThrow();
        assertTrue(left.center().x() > 0.0f,
                "WING_LEFT must sit on local +X; found it at x=" + left.center().x());

        Obb placed = new Obb(HitPart.WING_LEFT);
        placed.place(left, 0, 64, 0, ShahedHitboxes.bodyRotation(0, 0, 0, new Quaternionf()));
        assertTrue(placed.center().x > 0.0,
                "placed heading south, the left wing must end up east of the fuselage");
    }
}
