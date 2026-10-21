package com.synarsis.airtacticalarsenal.entity.tel.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Suspension: the launcher on its eight wheels and eight skids")
class SuspensionTest {

    private static final double G = 0.06;

    private static double[] ground(DoubleBinaryOperator height) {
        double[] g = new double[Suspension.WHEELS];
        for (int i = 0; i < g.length; i++) {
            g[i] = height.applyAsDouble(Suspension.wheelX(i), Suspension.wheelZ(i));
        }
        return g;
    }

    private static double[] skids(DoubleBinaryOperator height) {
        double[] g = new double[Suspension.SKIDS];
        for (int i = 0; i < g.length; i++) {
            g[i] = height.applyAsDouble(Suspension.skidX(i), Suspension.skidZ(i));
        }
        return g;
    }

    private static Suspension.Input wheels(DoubleBinaryOperator height) {
        return Suspension.Input.wheelsOnly(ground(height));
    }

    private static Suspension.Input none() {
        double[] g = new double[Suspension.WHEELS];
        Arrays.fill(g, Double.NaN);
        return Suspension.Input.wheelsOnly(g);
    }

    private static void settle(Suspension.State s, Suspension.Input in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Suspension.step(s, in, false, G);
        }
    }

    private static void assertNotSunk(Suspension.State s, Suspension.Input in) {
        for (int i = 0; i < Suspension.WHEELS; i++) {
            double g = in.wheel()[i];
            if (Double.isNaN(g)) {
                continue;
            }
            double sunk = g - Suspension.bottom(s.y, s.pitch, s.roll, i);
            assertTrue(sunk <= Suspension.SQUASH + 0.003, "wheel " + i + " is " + sunk + " into the ground");
        }
        for (int i = 0; i < Suspension.SKIDS; i++) {
            double g = in.skid()[i];
            if (Double.isNaN(g)) {
                continue;
            }
            double sunk = g - Suspension.skidBottom(s.y, s.pitch, s.roll, i);
            assertTrue(sunk <= Suspension.SKID_SQUASH + 0.003, "skid " + i + " is " + sunk + " into the ground");
        }
    }

    @Test
    @DisplayName("on level ground it lies level, standing on the ground")
    void levelGround() {
        Suspension.State s = new Suspension.State();
        s.y = 0.5;
        Suspension.Input in = wheels((x, z) -> 0.5);
        settle(s, in, 100);
        assertEquals(0.5, s.y, 1.0E-6);
        assertEquals(0.0, s.pitch, 1.0E-6);
        assertEquals(0.0, s.roll, 1.0E-6);
        assertTrue(Suspension.step(s, in, false, G).onGround());
    }

    @Test
    @DisplayName("set down below the ground it eases up to it instead of jumping")
    void risesGraduallyFromBelow() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> 0.9);
        double last = s.y;
        for (int i = 0; i < 60; i++) {
            Suspension.step(s, in, false, G);
            assertTrue(s.y - last <= Suspension.MAX_RISE_PER_TICK + 1.0E-9, "jumped " + (s.y - last));
            assertTrue(s.y >= last - 1.0E-9);
            last = s.y;
        }
        assertEquals(0.9, s.y, 1.0E-3);
    }

    @Test
    @DisplayName("the front axles on a one-block ledge: nose up, and the body climbs without a jump")
    void ledgeAhead() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> z > 0 ? 1.0 : 0.0);
        double last = s.y;
        double biggest = 0.0;
        for (int i = 0; i < 80; i++) {
            Suspension.step(s, in, false, G);
            biggest = Math.max(biggest, Math.abs(s.y - last));
            last = s.y;
        }
        assertTrue(s.pitch < -3.0, "nose should be up, pitch " + s.pitch);
        assertTrue(s.y > 0.3 && s.y < 1.0, "y " + s.y);
        assertTrue(biggest < 0.3, "a tick moved the body " + biggest);
        assertNotSunk(s, in);
    }

    @Test
    @DisplayName("the first tick of a ledge does not lift the whole vehicle")
    void firstTickIsSmall() {
        Suspension.State s = new Suspension.State();
        Suspension.step(s, wheels((x, z) -> z > 0 ? 1.0 : 0.0), false, G);
        assertTrue(s.y < 0.3, "y after one tick " + s.y);
        assertTrue(s.pitch < 0.0);
    }

    @Test
    @DisplayName("ground higher on the left rolls the left side up")
    void sideSlope() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> 0.2 * x);
        settle(s, in, 200);
        assertEquals(Math.toDegrees(Math.atan(0.2)), s.roll, 0.5);
        assertEquals(0.0, s.pitch, 0.5);
        assertNotSunk(s, in);
    }

    @Test
    @DisplayName("ground falling away ahead tips the nose down")
    void downhillAhead() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> -0.2 * z);
        settle(s, in, 200);
        assertTrue(s.pitch > 5.0, "pitch " + s.pitch);
    }

    @Test
    @DisplayName("with nothing under it, it falls at gravity")
    void freeFall() {
        Suspension.State s = new Suspension.State();
        for (int i = 1; i <= 10; i++) {
            Suspension.Result r = Suspension.step(s, none(), false, G);
            assertEquals(-G * i, s.vy, 1.0E-9);
            assertFalse(r.onGround());
            assertFalse(r.contact());
        }
    }

    @Test
    @DisplayName("landing reports the speed, and a hard one bounces a little")
    void landing() {
        Suspension.State s = new Suspension.State();
        for (int i = 0; i < 10; i++) {
            Suspension.step(s, none(), false, G);
        }
        double fallSpeed = -s.vy - G;

        Suspension.Input floor = wheels((x, z) -> s.y - 0.5);
        Suspension.Result r = Suspension.step(s, floor, false, G);
        for (int i = 0; i < 5 && r.landingSpeed() == 0.0; i++) {
            r = Suspension.step(s, floor, false, G);
        }
        assertTrue(r.landingSpeed() > fallSpeed, "landing speed " + r.landingSpeed());
        assertTrue(s.vy > 0.0 && s.vy < 0.2, "bounce " + s.vy);

        settle(s, floor, 300);
        assertEquals(floor.wheel()[0], s.y, 1.0E-3);
        assertEquals(0.0, s.vy, 1.0E-9);
    }

    @Test
    @DisplayName("a soft landing does not bounce")
    void softLanding() {
        Suspension.State s = new Suspension.State();
        s.y = 0.5;
        s.airborne = true;
        Suspension.Input floor = wheels((x, z) -> 0.0);
        Suspension.Result r = null;
        for (int i = 0; i < 30 && (r == null || r.landingSpeed() == 0.0); i++) {
            r = Suspension.step(s, floor, false, G);
        }
        assertTrue(r.landingSpeed() > 0.0 && r.landingSpeed() < 0.4);
        assertTrue(s.vy >= 0.0 && s.vy < 0.1);
    }

    @Test
    @DisplayName("one wheel in a hollow, seven on the ground: the body stands on the seven")
    void oneWheelInAHole() {
        Suspension.State s = new Suspension.State();
        double[] g = ground((x, z) -> 0.0);
        g[0] = -1.0;
        Suspension.Input in = Suspension.Input.wheelsOnly(g);
        settle(s, in, 200);
        assertTrue(s.y > -0.2, "sank to " + s.y);
        assertNotSunk(s, in);

        Suspension.State t = new Suspension.State();
        double[] h = ground((x, z) -> 0.0);
        h[0] = Double.NaN;
        settle(t, Suspension.Input.wheelsOnly(h), 100);
        assertEquals(0.0, t.y, 1.0E-6);
        assertEquals(0.0, t.pitch, 1.0E-6);
        assertEquals(0.0, t.roll, 1.0E-6);
    }

    @Test
    @DisplayName("a bump under one wheel lifts the body so no tyre is squashed past its limit")
    void liftsOntoTheHighestWheel() {
        Suspension.State s = new Suspension.State();
        double[] g = ground((x, z) -> 0.0);
        g[3] = 0.7;
        Suspension.Input in = Suspension.Input.wheelsOnly(g);
        settle(s, in, 300);
        assertNotSunk(s, in);
        assertTrue(s.y > 0.0);
    }

    @Test
    @DisplayName("every settled pose stands on the ground, whatever the steps under it")
    void everySettledPoseStandsOnTheGround() {
        for (double bump : new double[] {0.2, 0.5, 0.9, 1.0}) {
            Suspension.State s = new Suspension.State();
            Suspension.Input in = wheels((x, z) -> z > 3 ? bump : (x > 0 && z < -3 ? bump * 0.5 : 0.0));
            settle(s, in, 300);
            assertNotSunk(s, in);
        }
    }

    @Test
    @DisplayName("too few wheels to say how it lies: angles are kept")
    void tooFewContactsKeepTheAngles() {
        double[] g = new double[Suspension.WHEELS];
        Arrays.fill(g, Double.NaN);
        g[0] = 0.0;
        g[7] = 0.0;
        Suspension.Target t = Suspension.target(0.0, 4.0, -3.0, Suspension.Input.wheelsOnly(g));
        assertTrue(t.contact());
        assertFalse(t.plane());
        assertEquals(4.0, t.pitchDeg(), 0.0);
        assertEquals(-3.0, t.rollDeg(), 0.0);
    }

    @Test
    @DisplayName("afloat with nothing underneath, the angles die away")
    void floatingLevelsOut() {
        Suspension.State s = new Suspension.State();
        s.pitch = 10.0;
        s.roll = -10.0;
        for (int i = 0; i < 30; i++) {
            Suspension.step(s, none(), true, G);
        }
        assertTrue(Math.abs(s.pitch) < 1.0 && Math.abs(s.roll) < 1.0);
    }

    @Test
    @DisplayName("the springs overshoot a little but settle")
    void springSettles() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> 0.2 * x);
        double target = Math.toDegrees(Math.atan(0.2));
        double peak = 0.0;
        for (int i = 0; i < 200; i++) {
            Suspension.step(s, in, false, G);
            peak = Math.max(peak, s.roll);
        }
        assertTrue(peak > target, "a lively spring should overshoot, peak " + peak);
        assertTrue(peak < target * 1.4, "but not by much, peak " + peak);
        assertEquals(target, s.roll, 0.1);
    }

    @Test
    @DisplayName("a terrace of one block across the way: the front wheels climb on, and the belly box clears the edge")
    void terraceOneBlock() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = new Suspension.Input(
                ground((x, z) -> z > 2.0 ? 1.0 : 0.0), null, skids((x, z) -> z > 2.0 ? 1.0 : 0.0));
        settle(s, in, 120);
        assertTrue(s.y > 0.3, "the body should have come up, y " + s.y);
        assertTrue(s.pitch < -2.0, "nose up, pitch " + s.pitch);

        AABB terrace = new AABB(-8, 0, 2.0, 8, 1.0, 20);
        Obb wallHull = Obb.wallHull(0, s.y, 0, 0, (float) s.pitch, (float) s.roll);
        assertFalse(ObbSweep.intersects(wallHull, List.of(terrace), 0.1),
                "the belly box must not be held by the terrace edge");
        ObbSweep.Result r = ObbSweep.resolve(wallHull, 0, 2.0, List.of(terrace), ObbSweep.Params.STANDARD);
        assertEquals(2.0, r.dz(), 1.0E-9);
    }

    @Test
    @DisplayName("a ridge of one block between the axles: the skids take the weight and the body rides over")
    void ridgeBetweenTheAxles() {
        Suspension.State s = new Suspension.State();
        DoubleBinaryOperator ridge = (x, z) -> (z > -1.0 && z < 2.5) ? 1.0 : 0.0;
        Suspension.Input in = new Suspension.Input(ground((x, z) -> 0.0), null, skids(ridge));
        Suspension.Result r = null;
        for (int i = 0; i < 80; i++) {
            r = Suspension.step(s, in, false, G);
        }
        assertTrue(r.skidSupports() >= 2, "skids carrying: " + r.skidSupports());
        assertTrue(s.y > 0.3, "the belly should ride up on the ridge, y " + s.y);
        assertNotSunk(s, in);

        AABB ridgeBox = new AABB(-8, 0, -1.0, 8, 1.0, 2.5);
        Obb wallHull = Obb.wallHull(0, s.y, 0, 0, (float) s.pitch, (float) s.roll);
        assertFalse(ObbSweep.intersects(wallHull, List.of(ridgeBox), 0.1), "a ridge is not a wall");
    }

    @Test
    @DisplayName("over a ridge the traction is halved, but only on the belly alone")
    void bellyOnlyIsReported() {
        Suspension.State s = new Suspension.State();
        double[] none = new double[Suspension.WHEELS];
        Arrays.fill(none, Double.NaN);
        Suspension.Input in = new Suspension.Input(none, null, skids((x, z) -> (z > -1.0 && z < 2.5) ? 1.0 : -5.0));
        Suspension.Result r = Suspension.step(s, in, false, G);
        assertEquals(0, r.wheelContacts());
        assertTrue(r.skidSupports() > 0);
        assertTrue(r.contact());
    }

    @Test
    @DisplayName("driving off a terrace nose first: when the front bogie finds nothing, the nose goes down")
    void noseTipsOverTheEdge() {
        Suspension.State s = new Suspension.State();
        double[] g = ground((x, z) -> z < 0 ? 0.0 : Double.NaN);
        Suspension.Input in = Suspension.Input.wheelsOnly(g);

        Suspension.Target t = Suspension.target(0, 0, 0, in);
        assertTrue(t.lopsided(), "all the support is behind the centre of mass");

        double last = 0.0;
        for (int i = 0; i < 25; i++) {
            Suspension.step(s, in, false, G);
            assertTrue(s.pitch >= last - 0.2, "the nose must keep going down, pitch " + s.pitch + " after " + last);
            last = s.pitch;
        }
        assertTrue(s.pitch > 8.0, "pitch " + s.pitch);
        for (int i = 0; i < 4; i++) {
            double bottom = Suspension.bottom(s.y, s.pitch, s.roll, i);
            assertTrue(bottom > -Suspension.SQUASH - 0.1, "rear wheel " + i + " sank to " + bottom);
        }

        Suspension.Input lower = wheels((x, z) -> z < 0 ? 0.0 : -1.5);
        for (int i = 0; i < 120; i++) {
            Suspension.step(s, lower, false, G);
        }
        assertTrue(s.pitch > 5.0 && s.pitch < 20.0, "pitch on the ledge " + s.pitch);
        assertFalse(Suspension.target(s.y, s.pitch, s.roll, lower).lopsided());
    }

    @Test
    @DisplayName("backing off the edge tips the tail the same way")
    void tailTipsOverTheEdge() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> z > 0 ? 0.0 : Double.NaN);
        for (int i = 0; i < 25; i++) {
            Suspension.step(s, in, false, G);
        }
        assertTrue(s.pitch < -8.0, "pitch " + s.pitch);
    }

    @Test
    @DisplayName("everything on one flank rolls the body over that side")
    void flankTipsOverTheEdge() {
        Suspension.State s = new Suspension.State();
        Suspension.Input in = wheels((x, z) -> x > 0 ? 0.0 : Double.NaN);
        for (int i = 0; i < 25; i++) {
            Suspension.step(s, in, false, G);
        }
        assertTrue(Math.abs(s.roll) > 8.0, "roll " + s.roll);
    }

    @Test
    @DisplayName("a ledge of exactly one block is ground, not a wall, even with the tyre squashed")
    void ledgeOfOneBlockIsNotAWall() {
        double squashedBottom = -Suspension.SQUASH;
        assertEquals(Support.CONTACT, Support.classify(1.0, squashedBottom, TelGeometry.CLIMB));
        assertTrue(1.0 > squashedBottom + 1.05);
        assertEquals(Support.WALL, Support.classify(1.5, squashedBottom, TelGeometry.CLIMB));
        assertEquals(Support.HANGING, Support.classify(Double.NaN, 0.0, TelGeometry.CLIMB));
        assertEquals(Support.CONTACT, Support.classify(0.0, 0.0, TelGeometry.CLIMB));
    }

    private static double patch(DoubleUnaryOperator profile, double z) {
        return Math.max(profile.applyAsDouble(z - 0.45), Math.max(profile.applyAsDouble(z), profile.applyAsDouble(z + 0.45)));
    }

    private static Suspension.Input along(DoubleUnaryOperator profile, double originZ) {
        double[] wheel = new double[Suspension.WHEELS];
        double[] skid = new double[Suspension.SKIDS];
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = patch(profile, originZ + Suspension.wheelZ(i));
        }
        for (int i = 0; i < skid.length; i++) {
            skid[i] = patch(profile, originZ + Suspension.skidZ(i));
        }
        return new Suspension.Input(wheel, null, skid);
    }

    @Test
    @DisplayName("a 20 degree descent at 1.0 blocks a tick (about 73 km/h) never takes off")
    void steepDescentStaysOnTheGround() {
        double slope = Math.tan(Math.toRadians(20.0));
        DoubleUnaryOperator profile = z -> z < 10.0 ? 0.0 : -slope * (z - 10.0);
        Suspension.State s = new Suspension.State();
        double z = -30.0;
        for (int i = 0; i < 20; i++) {
            Suspension.step(s, along(profile, z), false, G);
        }
        for (int tick = 0; tick < 120; tick++) {
            z += 1.0;
            Suspension.Result r = Suspension.step(s, along(profile, z), false, G);
            assertFalse(s.airborne, "took off at tick " + tick + ", z " + z);
            assertEquals(0.0, r.landingSpeed(), 0.0, "landed at tick " + tick);
            double ahead = profile.applyAsDouble(z + 5.0);
            assertTrue(s.y > ahead - 2.5 && s.y < profile.applyAsDouble(z - 5.0) + 2.5,
                    "y " + s.y + " at z " + z);
        }
        assertTrue(s.pitch > 5.0, "nose should follow the slope down, pitch " + s.pitch);
    }

    @Test
    @DisplayName("a 20 degree climb is followed too, with the nose up")
    void steepClimb() {
        double slope = Math.tan(Math.toRadians(20.0));
        DoubleUnaryOperator profile = z -> z < 10.0 ? 0.0 : slope * (z - 10.0);
        Suspension.State s = new Suspension.State();
        double z = -30.0;
        for (int tick = 0; tick < 140; tick++) {
            z += 1.0;
            Suspension.step(s, along(profile, z), false, G);
            assertFalse(s.airborne);
        }
        assertTrue(s.pitch < -5.0, "pitch " + s.pitch);
    }

    @Test
    @DisplayName("a drop of two blocks is a fall and a landing, not a glide")
    void twoBlockDrop() {
        DoubleUnaryOperator profile = z -> z < 10.0 ? 0.0 : -2.0;
        Suspension.State s = new Suspension.State();
        double z = -10.0;
        boolean tookOff = false;
        double landing = 0.0;
        for (int tick = 0; tick < 120; tick++) {
            z += 1.0;
            Suspension.Result r = Suspension.step(s, along(profile, z), false, G);
            tookOff |= s.airborne;
            landing = Math.max(landing, r.landingSpeed());
        }
        assertTrue(tookOff, "it should have dropped, not slid down");
        assertTrue(landing > 0.0, "and landed");
        assertEquals(-2.0, s.y, 0.2);
        assertFalse(s.airborne);
        assertNotNull(s);
    }
}
