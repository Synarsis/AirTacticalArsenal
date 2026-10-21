package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitScan;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.TelSequence;
import com.synarsis.airtacticalarsenal.entity.tel.TelState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Launcher hit boxes read from the model")
class TelModelHitboxTest {

    private static final String DEPLOY_LEFT = "animation.iskander_tel_loaded.deploy_left";
    private static final TelModelHitbox MODEL = TelModelHitbox.get();

    private static List<WorldBox> parked() {
        return MODEL.compute(TelHitboxPose.PARKED, 0, 0, 0, 0f, 0f, 0f);
    }

    private static List<WorldBox> raisedFirstRound() {
        return MODEL.compute(TelHitboxPose.single(DEPLOY_LEFT, 7.8, 1.0, true, true), 0, 0, 0, 0f, 0f, 0f);
    }

    @Test
    @DisplayName("the model loads, and fits in a workable number of boxes")
    void loads() {
        assertTrue(MODEL.cubeCount() > 800, "cubes " + MODEL.cubeCount());
        assertTrue(MODEL.boxCount() > 20 && MODEL.boxCount() <= TelModelHitbox.TARGET_BOXES,
                "boxes " + MODEL.boxCount() + " at ratio " + MODEL.mergeRatio());
        assertEquals(MODEL.boxCount(), parked().size());
    }

    @Test
    @DisplayName("every zone has boxes")
    void everyZoneIsPopulated() {
        for (Zone zone : Zone.values()) {
            assertTrue(MODEL.countIn(zone) > 0, "no boxes in " + zone);
        }
    }

    @Test
    @DisplayName("the animation ends with the erector upright and the cover open")
    void samplerEndOfDeploy() {
        TelAnimSampler sampler = MODEL.sampler();
        assertTrue(sampler.has(DEPLOY_LEFT));
        var end = sampler.sample(DEPLOY_LEFT, 7.8);
        assertEquals(90.0, end.get("erector_left").rotation()[0], 1.0E-3);
        assertEquals(150.0, end.get("cover_left").rotation()[2], 1.0E-3);
        assertEquals(3.5, end.get("erector_left").position()[1], 1.0E-3);
        assertEquals(12.0, end.get("erector_left").position()[2], 1.0E-3);
        assertEquals(0.0, end.get("erector_right").rotation()[0], 1.0E-3);
        assertEquals(150.0, sampler.sample(DEPLOY_LEFT, 99.0).get("cover_left").rotation()[2], 1.0E-3);
        assertEquals(0.0, sampler.sample(DEPLOY_LEFT, 0.0).get("cover_left").rotation()[2], 1.0E-3);
    }

    @Test
    @DisplayName("easing shapes the stretch up to a key")
    void samplerEasing() {
        var half = MODEL.sampler().sample(DEPLOY_LEFT, 2.7917 / 2.0).get("cover_left").rotation()[2];
        assertEquals(75.0, half, 0.01);
        var quarter = MODEL.sampler().sample(DEPLOY_LEFT, 2.7917 / 4.0).get("cover_left").rotation()[2];
        assertTrue(quarter < 37.5 - 5.0, "quarter " + quarter);
    }

    @Test
    @DisplayName("self-check: the foot of the raised round is where the geometry says it is")
    void raisedRoundStandsOnTheSpecifiedBase() {
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (WorldBox b : raisedFirstRound()) {
            if (b.zone() == Zone.MISSILE_1) {
                lowest = Math.min(lowest, b.minY());
                highest = Math.max(highest, b.maxY());
            }
        }
        Vec3 base = TelGeometry.MISSILE_BASE[0];
        assertEquals(base.y, lowest, 0.05, "foot height");
        assertEquals(TelGeometry.MISSILE_TOP_Y, highest, 0.1, "tip height");

        double sx = 0.0;
        double sz = 0.0;
        int n = 0;
        for (WorldBox b : raisedFirstRound()) {
            if (b.zone() == Zone.MISSILE_1 && b.minY() < lowest + 0.1) {
                sx += b.centre().x;
                sz += b.centre().z;
                n++;
            }
        }
        assertTrue(n > 0);
        assertEquals(base.x, sx / n, 0.05, "foot x");
        assertEquals(base.z, sz / n, 0.05, "foot z");
    }

    @Test
    @DisplayName("the pose the cycle gives a raised round puts its foot on MISSILE_BASE[0]")
    void poseFromTheCycleMatchesTheGeometry() {
        TelSequence seq = new TelSequence();
        seq.setLoaded(0, true);
        seq.setLoaded(1, true);
        assertTrue(seq.tryPrepare(1).ok());
        for (int i = 0; i < 1000 && seq.state() != TelState.RAISED; i++) {
            seq.tick();
        }
        assertEquals(TelState.RAISED, seq.state());
        TelHitboxPose pose = seq.hitboxPose(0.0f);
        assertEquals("animation.iskander_tel_loaded.raise_left", pose.lift1().clip());
        assertEquals(TelPose.HELD_SECONDS, pose.lift1().seconds());
        assertEquals("animation.iskander_tel_loaded.cover_open", pose.covers().clip());
        assertEquals(1.0, pose.jackProgress(), 1.0e-6, "jacks are out while raised");

        List<WorldBox> boxes = MODEL.compute(pose, 0, 0, 0, 0f, 0f, 0f);
        double lowest = Double.MAX_VALUE;
        double sx = 0.0;
        double sz = 0.0;
        int n = 0;
        for (WorldBox b : boxes) {
            if (b.zone() == Zone.MISSILE_1) {
                lowest = Math.min(lowest, b.minY());
            }
        }
        for (WorldBox b : boxes) {
            if (b.zone() == Zone.MISSILE_1 && b.minY() < lowest + 0.1) {
                sx += b.centre().x;
                sz += b.centre().z;
                n++;
            }
        }
        Vec3 base = TelGeometry.MISSILE_BASE[0];
        assertTrue(n > 0);
        assertEquals(base.y, lowest, 0.05, "foot height");
        assertEquals(base.x, sx / n, 0.05, "foot x");
        assertEquals(base.z, sz / n, 0.05, "foot z");
    }

    @Test
    @DisplayName("each erector stands on its own base whether the other is up or down")
    void erectorsAreIndependent() {
        TelHitboxPose.Part up0 = TelPose.part(TelBoneGroups.LIFT_1, TelPose.Clip.HELD, 0);
        TelHitboxPose.Part up1 = TelPose.part(TelBoneGroups.LIFT_2, TelPose.Clip.HELD, 0);
        TelHitboxPose.Part open = TelPose.part(TelBoneGroups.COVERS, TelPose.Clip.HELD, 0);
        for (int mask = 1; mask <= 3; mask++) {
            TelHitboxPose pose = new TelHitboxPose(open, (mask & 1) != 0 ? up0 : TelHitboxPose.Part.REST,
                    (mask & 2) != 0 ? up1 : TelHitboxPose.Part.REST, 1.0, true, true);
            List<WorldBox> boxes = MODEL.compute(pose, 0, 0, 0, 0f, 0f, 0f);
            for (int slot = 0; slot < 2; slot++) {
                Zone zone = slot == 0 ? Zone.MISSILE_1 : Zone.MISSILE_2;
                double lowest = Double.MAX_VALUE;
                double highest = -Double.MAX_VALUE;
                for (WorldBox b : boxes) {
                    if (b.zone() == zone) {
                        lowest = Math.min(lowest, b.minY());
                        highest = Math.max(highest, b.maxY());
                    }
                }
                if ((mask & (1 << slot)) != 0) {
                    assertEquals(TelGeometry.MISSILE_BASE[slot].y, lowest, 0.05, "foot of " + slot + " in " + mask);
                    assertEquals(TelGeometry.MISSILE_TOP_Y, highest, 0.1, "tip of " + slot + " in " + mask);
                } else {
                    assertTrue(highest < 4.0, "round " + slot + " should lie down in " + mask + ", tops at " + highest);
                }
            }
        }
    }

    @Test
    @DisplayName("the covers open without a lift moving, and a lift rises with the covers shut or open as asked")
    void groupsAreIndependent() {
        TelHitboxPose shut = TelHitboxPose.PARKED;
        TelHitboxPose open = new TelHitboxPose(TelPose.part(TelBoneGroups.COVERS, TelPose.Clip.HELD, 0),
                TelHitboxPose.Part.REST, TelHitboxPose.Part.REST, 0.0, true, true);
        double shutSpan = MODEL.compute(shut, 0, 0, 0, 0f, 0f, 0f).stream().filter(b -> b.zone() == Zone.COVER)
                .mapToDouble(b -> Math.abs(b.centre().x) + b.hx()).max().orElse(0);
        double openSpan = MODEL.compute(open, 0, 0, 0, 0f, 0f, 0f).stream().filter(b -> b.zone() == Zone.COVER)
                .mapToDouble(b -> Math.abs(b.centre().x) + b.hx()).max().orElse(0);
        assertTrue(openSpan != shutSpan && Math.abs(openSpan - shutSpan) > 0.3, "open " + openSpan + " shut " + shutSpan);
        double lie = MODEL.compute(open, 0, 0, 0, 0f, 0f, 0f).stream().filter(b -> b.zone() == Zone.MISSILE_2)
                .mapToDouble(WorldBox::maxY).max().orElse(0);
        assertTrue(lie < 4.0);
    }

    @Test
    @DisplayName("the second round is the mirror image, and stays down in the left-hand clip")
    void secondRoundStaysDown() {
        double rightLowest = Double.MAX_VALUE;
        double rightHighest = -Double.MAX_VALUE;
        for (WorldBox b : raisedFirstRound()) {
            if (b.zone() == Zone.MISSILE_2) {
                rightLowest = Math.min(rightLowest, b.minY());
                rightHighest = Math.max(rightHighest, b.maxY());
            }
        }
        assertTrue(rightHighest < 4.0, "still lying down, tops out at " + rightHighest);
        assertTrue(rightLowest < 3.0);
    }

    @Test
    @DisplayName("a missing round has no boxes")
    void emptySlotsAreLeftOut() {
        List<WorldBox> none = MODEL.compute(TelHitboxPose.PARKED.withLoaded(false, true), 0, 0, 0, 0f, 0f, 0f);
        assertFalse(none.stream().anyMatch(b -> b.zone() == Zone.MISSILE_1));
        assertTrue(none.stream().anyMatch(b -> b.zone() == Zone.MISSILE_2));
    }

    @Test
    @DisplayName("the parked model sits on the ground within its stated size")
    void sizeIsAboutRight() {
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (WorldBox b : parked()) {
            minY = Math.min(minY, b.minY());
            maxY = Math.max(maxY, b.maxY());
            minZ = Math.min(minZ, b.centre().z - 0.0);
            maxZ = Math.max(maxZ, b.centre().z);
            maxX = Math.max(maxX, Math.abs(b.centre().x) + b.hx());
        }
        assertEquals(0.0, minY, 0.1);
        assertEquals(4.0, maxY, 0.3);
        assertTrue(maxX < 2.2, "width " + maxX);
        assertTrue(minZ < -5.0 && maxZ > 5.0);
    }

    @Test
    @DisplayName("a yawed launcher's boxes turn with it")
    void boxesFollowTheYaw() {
        List<WorldBox> a = MODEL.compute(TelHitboxPose.PARKED, 0, 0, 0, 0f, 0f, 0f);
        List<WorldBox> b = MODEL.compute(TelHitboxPose.PARKED, 0, 0, 0, 90f, 0f, 0f);
        WorldBox cab0 = a.stream().filter(x -> x.zone() == Zone.CAB).findFirst().orElseThrow();
        WorldBox cab1 = b.stream().filter(x -> x.zone() == Zone.CAB).findFirst().orElseThrow();
        assertEquals(cab0.centre().z, -cab1.centre().x, 1.0E-6);
        assertEquals(cab0.centre().x, cab1.centre().z, 1.0E-6);
    }

    @Test
    @DisplayName("a shot through the open gap above the deck, between cab and bay, goes through")
    void shotThroughTheGap() {
        List<Obb> obbs = TelHitboxQuery.toObbs(parked());
        HitScan.BoxHit hit = HitScan.nearestBox(obbs, new Vec3(-6, 3.85, 3.6), new Vec3(6, 3.85, 3.6));
        assertNull(hit, hit == null ? "" : "hit " + hit.box().tag().id());
    }

    @Test
    @DisplayName("a shot into the side of the cab hits the cab, and the side of the bay hits the bay")
    void shotIntoTheSide() {
        List<Obb> obbs = TelHitboxQuery.toObbs(parked());
        HitScan.BoxHit cab = HitScan.nearestBox(obbs, new Vec3(-6, 2.5, 6.0), new Vec3(6, 2.5, 6.0));
        assertNotNull(cab);
        assertTrue(cab.box().tag() == Zone.CAB || cab.box().tag() == Zone.CHASSIS || cab.box().tag() == Zone.ENGINE,
                "hit " + cab.box().tag().id());
        assertTrue(Math.abs(cab.point().x) > 1.0 && Math.abs(cab.point().x) < 2.3, "entered at " + cab.point());

        HitScan.BoxHit bay = HitScan.nearestBox(obbs, new Vec3(-6, 2.0, -5.0), new Vec3(6, 2.0, -5.0));
        assertNotNull(bay);
    }

    @Test
    @DisplayName("a shot well wide of the machine misses")
    void shotWide() {
        List<Obb> obbs = TelHitboxQuery.toObbs(parked());
        assertNull(HitScan.nearestBox(obbs, new Vec3(-6, 2.5, 20.0), new Vec3(6, 2.5, 20.0)));
        assertNull(HitScan.nearestBox(obbs, new Vec3(2.6, 2.0, -20.0), new Vec3(2.6, 2.0, 20.0)));
    }

    @Test
    @DisplayName("the corner of the old four-by-four part box that is not the machine is not a hit")
    void emptyCornerOfAPartBox() {
        List<Obb> obbs = TelHitboxQuery.toObbs(parked());
        AABB cornerOfPart = new AABB(1.9, 3.9, 4.0, 2.0, 4.0, 8.0);
        HitScan.BoxHit hit = HitScan.nearestBox(obbs, new Vec3(cornerOfPart.minX + 0.05, 7.0, 6.0),
                new Vec3(cornerOfPart.minX + 0.05, 3.5, 6.0));
        assertNull(hit);
    }

    private static WorldBox biggestSolid(List<WorldBox> boxes) {
        WorldBox best = null;
        double bestVolume = 0.0;
        for (WorldBox b : boxes) {
            boolean aligned = Math.abs(b.ax().x) > 0.999 && Math.abs(b.ay().y) > 0.999 && Math.abs(b.az().z) > 0.999;
            double v = b.hx() * b.hy() * b.hz();
            if (b.solid() && aligned && v > bestVolume) {
                best = b;
                bestVolume = v;
            }
        }
        return best;
    }

    @Test
    @DisplayName("a good part of the machine is solid")
    void somethingIsSolid() {
        long solid = parked().stream().filter(WorldBox::solid).count();
        assertTrue(solid >= 10, "solid boxes " + solid);
    }

    @Test
    @DisplayName("something pressed into the side of the machine is moved out")
    void pushedOutFromTheSide() {
        List<WorldBox> boxes = parked();
        WorldBox big = biggestSolid(boxes);
        assertNotNull(big);
        double face = big.centre().x + big.hx();
        AABB entity = new AABB(face - 0.15, big.centre().y - 0.2, big.centre().z - 0.2,
                face + 0.45, big.centre().y + 0.2, big.centre().z + 0.2);
        TelSolidity.Push push = TelSolidity.resolve(boxes, entity);
        assertFalse(push.isNone(), "should be moved");
        assertFalse(push.standing());
        assertTrue(TelSolidity.resolve(boxes, entity.move(push.offset())).isNone());
    }

    @Test
    @DisplayName("something that has sunk into the top of the machine is lifted onto it and counts as standing")
    void liftedOntoTheRoof() {
        List<WorldBox> boxes = parked();
        WorldBox top = null;
        for (WorldBox b : boxes) {
            if (b.solid() && b.hx() > 0.3 && b.hz() > 0.3 && (top == null || b.maxY() > top.maxY())) {
                top = b;
            }
        }
        assertNotNull(top);
        double roof = top.maxY();
        AABB entity = new AABB(top.centre().x - 0.15, roof - 0.1, top.centre().z - 0.15,
                top.centre().x + 0.15, roof + 1.7, top.centre().z + 0.15);
        TelSolidity.Push push = TelSolidity.resolve(boxes, entity);
        assertTrue(push.standing(), "standing, offset " + push.offset());
        assertEquals(0.1, push.offset().y, 0.05);
    }

    @Test
    @DisplayName("someone far from the machine is left alone")
    void faraway() {
        assertTrue(TelSolidity.resolve(parked(), new AABB(10, 0, 10, 10.6, 1.8, 10.6)).isNone());
    }
}
