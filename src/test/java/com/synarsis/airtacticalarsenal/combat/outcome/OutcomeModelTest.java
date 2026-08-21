package com.synarsis.airtacticalarsenal.combat.outcome;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("What losing a section does, and how the wreck comes down")
class OutcomeModelTest {

    private static FallController.State level(float yaw) {
        double rad = Math.toRadians(yaw);
        return new FallController.State(new Vec3(Math.sin(rad) * 1.5, 0, Math.cos(rad) * 1.5), yaw, 0, 0);
    }

    private static FallController.State run(FallMode mode, int ticks, int sign, FallController.State from) {
        FallController.State s = from;
        for (int i = 0; i < ticks; i++) {
            s = FallController.step(mode, i, sign, s, FallController.Tuning.DEFAULT);
        }
        return s;
    }

    // ---------------------------------------------------------------- outcomes

    @Test
    @DisplayName("every section maps to an outcome, and none of them throws")
    void everyPartResolves() {
        for (HitPart part : HitPart.values()) {
            assertNotEquals(null, OutcomeResolver.onKnockedOut(part, FallMode.NONE), part.id());
        }
    }

    @Test
    @DisplayName("the table from the design holds")
    void designTableHolds() {
        assertEquals(FallMode.SPIRAL, OutcomeResolver.onKnockedOut(HitPart.WING_LEFT, FallMode.NONE));
        assertEquals(FallMode.SPIRAL, OutcomeResolver.onKnockedOut(HitPart.WING_RIGHT, FallMode.NONE));
        assertEquals(FallMode.TUMBLE, OutcomeResolver.onKnockedOut(HitPart.TAIL, FallMode.NONE));
        assertEquals(FallMode.GLIDE, OutcomeResolver.onKnockedOut(HitPart.ENGINE, FallMode.NONE));
        assertEquals(FallMode.GLIDE, OutcomeResolver.onKnockedOut(HitPart.PROP, FallMode.NONE));
        assertEquals(FallMode.BURNING, OutcomeResolver.onKnockedOut(HitPart.FUEL, FallMode.NONE));
        assertEquals(FallMode.VEER, OutcomeResolver.onKnockedOut(HitPart.AVIONICS, FallMode.NONE));
        assertEquals(FallMode.NONE, OutcomeResolver.onKnockedOut(HitPart.BODY, FallMode.NONE));
    }

    @Test
    @DisplayName("a later, lesser hit cannot rescue a drone that is already going down")
    void outcomesOnlyEscalate() {
        // Wing first, then the engine: it does not recover into a tidy glide.
        FallMode afterWing = OutcomeResolver.onKnockedOut(HitPart.WING_LEFT, FallMode.NONE);
        assertEquals(FallMode.SPIRAL, OutcomeResolver.onKnockedOut(HitPart.ENGINE, afterWing));
        // Losing structure afterwards changes nothing either.
        assertEquals(FallMode.SPIRAL, OutcomeResolver.onKnockedOut(HitPart.BODY, afterWing));
    }

    @Test
    @DisplayName("engine out on an already-damaged airframe is a worse descent")
    void deadStickWhenAlreadyHurt() {
        assertEquals(FallMode.DEAD_STICK, OutcomeResolver.onKnockedOut(HitPart.ENGINE, FallMode.BURNING));
    }

    @Test
    @DisplayName("a fire that spreads takes the airframe with it")
    void fireEventuallyEndsIt() {
        assertEquals(FallMode.SPIRAL, OutcomeResolver.onFireSpread(FallMode.BURNING));
        assertEquals(FallMode.NONE, OutcomeResolver.onFireSpread(FallMode.NONE), "nothing to spread to");
    }

    @Test
    @DisplayName("running out of durability drops an intact drone")
    void exhaustedDurabilityTumbles() {
        assertEquals(FallMode.TUMBLE, OutcomeResolver.onDurabilityExhausted(FallMode.NONE));
        assertEquals(FallMode.SPIRAL, OutcomeResolver.onDurabilityExhausted(FallMode.SPIRAL),
                "already falling: keep the mode it is in");
    }

    // ---------------------------------------------------------------- kinematics

    @Test
    @DisplayName("a glide trades height for distance and keeps its heading")
    void glideHoldsCourse() {
        FallController.State end = run(FallMode.GLIDE, 200, 1, level(90.0f));

        assertTrue(end.velocity().y < 0, "a glide must descend");
        assertEquals(90.0f, end.yaw(), 0.01f, "a glide holds the heading it had");
        double ratio = Math.sqrt(end.velocity().x * end.velocity().x + end.velocity().z * end.velocity().z)
                / -end.velocity().y;
        assertEquals(FallController.Tuning.DEFAULT.glideRatio(), ratio, 0.5,
                "forward travel per block of descent should settle near the glide ratio");
    }

    @Test
    @DisplayName("a spiral loses height monotonically and rolls into the missing wing")
    void spiralGoesDownAndBanks() {
        FallController.State s = level(0.0f);
        double previousSink = 0;
        for (int i = 0; i < 120; i++) {
            s = FallController.step(FallMode.SPIRAL, i, -1, s, FallController.Tuning.DEFAULT);
            assertTrue(s.velocity().y <= previousSink + 1.0E-6, "descent rate must not recover at tick " + i);
            previousSink = s.velocity().y;
        }
        assertTrue(s.roll() < -20.0f, "should be banked hard to the side it lost; roll=" + s.roll());
        assertTrue(s.velocity().y >= -FallController.Tuning.DEFAULT.terminalSpeed() - 1.0E-6,
                "descent must be capped");
    }

    @Test
    @DisplayName("a spiral turns the way the lost wing says")
    void spiralSignPicksTheSide() {
        FallController.State left = run(FallMode.SPIRAL, 40, 1, level(0.0f));
        FallController.State right = run(FallMode.SPIRAL, 40, -1, level(0.0f));
        assertTrue(left.roll() > 0 && right.roll() < 0,
                "the two wings must roll opposite ways; got " + left.roll() + " and " + right.roll());
    }

    @Test
    @DisplayName("a tumble keeps spinning without running away or going NaN")
    void tumbleStaysFinite() {
        FallController.State s = level(45.0f);
        for (int i = 0; i < 400; i++) {
            s = FallController.step(FallMode.TUMBLE, i, 1, s, FallController.Tuning.DEFAULT);
            assertFalse(Double.isNaN(s.velocity().x) || Double.isNaN(s.velocity().y)
                            || Float.isNaN(s.pitch()) || Float.isNaN(s.roll()),
                    "went NaN at tick " + i);
            assertTrue(Math.abs(s.pitch()) <= 180.01f && Math.abs(s.roll()) <= 180.01f,
                    "angles must stay wrapped; tick " + i);
        }
        assertTrue(s.velocity().y <= -FallController.Tuning.DEFAULT.terminalSpeed() + 0.01,
                "a tumble should reach terminal velocity");
    }

    @Test
    @DisplayName("a tumble never jumps: the attitude changes by one step a tick, wrap or no wrap")
    void tumbleAttitudeIsContinuous() {
        float step = (float) FallController.Tuning.DEFAULT.tumbleSpinRate();
        FallController.State s = level(0.0f);
        for (int i = 0; i < 300; i++) {
            FallController.State next = FallController.step(FallMode.TUMBLE, i, 1, s,
                    FallController.Tuning.DEFAULT);
            assertTrue(Math.abs(net.minecraft.util.Mth.degreesDifference(s.pitch(), next.pitch()))
                            <= step + 0.01f,
                    "pitch jumped at tick " + i + ": " + s.pitch() + " -> " + next.pitch());
            assertTrue(Math.abs(net.minecraft.util.Mth.degreesDifference(s.roll(), next.roll()))
                            <= step + 0.01f,
                    "roll jumped at tick " + i + ": " + s.roll() + " -> " + next.roll());
            s = next;
        }
    }

    @Test
    @DisplayName("entering a tumble mid-fall picks up from the attitude it already had")
    void tumbleResumesFromCurrentAttitude() {
        // A fire that spreads switches modes with the fall tick already well advanced.
        FallController.State midFall = new FallController.State(new Vec3(0.8, -1.2, 0), 0, -30.0f, 12.0f);
        FallController.State next = FallController.step(FallMode.TUMBLE, 240, 1, midFall,
                FallController.Tuning.DEFAULT);

        float step = (float) FallController.Tuning.DEFAULT.tumbleSpinRate();
        assertEquals(-30.0f + step, next.pitch(), 0.01f,
                "the spin must continue from the current pitch, not from where the tick count lands");
    }

    @Test
    @DisplayName("with guidance gone the drone keeps flying but walks off its bearing")
    void veerDriftsOffCourse() {
        FallController.State end = run(FallMode.VEER, 200, 1, level(0.0f));

        assertTrue(Math.abs(end.velocity().y) < 0.2, "it is still flying, not falling");
        assertNotEquals(0.0f, end.yaw(), "the heading has to have moved");
        assertTrue(Math.abs(end.yaw()) > 10.0f,
                "200 ticks of drift should be plainly off course; yaw=" + end.yaw());
    }

    @Test
    @DisplayName("a drone crippled while stationary still flies away, in every mode")
    void crippledFromAStandstillGetsMoving() {
        // The case a held test drone hits: released with no velocity at all. Modes that read the
        // velocity as found left it hanging in the air.
        FallController.State still = new FallController.State(Vec3.ZERO, 0, 0, 0);
        for (FallMode mode : FallMode.values()) {
            if (mode == FallMode.NONE) {
                continue;
            }
            FallController.State end = run(mode, 60, 1, still);
            double speed = Math.sqrt(end.velocity().x * end.velocity().x
                    + end.velocity().z * end.velocity().z);
            boolean moving = speed > 0.4 || end.velocity().y < -0.4;
            assertTrue(moving, mode.id() + " hung in the air: horizontal " + speed
                    + ", vertical " + end.velocity().y);
        }
    }

    @Test
    @DisplayName("a glide gets up to speed instead of bleeding off")
    void glideHoldsItsAirspeed() {
        FallController.State end = run(FallMode.GLIDE, 120, 1, new FallController.State(Vec3.ZERO, 0, 0, 0));
        double speed = Math.sqrt(end.velocity().x * end.velocity().x + end.velocity().z * end.velocity().z);
        assertEquals(FallController.Tuning.DEFAULT.glideSpeed(), speed, 0.1,
                "airspeed should settle on the glide speed, not decay");
        assertTrue(end.velocity().y < -0.4,
                "and the descent should be plain to see; sink=" + end.velocity().y);
    }

    @Test
    @DisplayName("a burning drone sinks slowly rather than dropping")
    void burningStillFlies() {
        FallController.State end = run(FallMode.BURNING, 100, 1, level(0.0f));
        assertTrue(end.velocity().y < 0, "it is losing height");
        assertTrue(end.velocity().y > -0.5, "but it has not fallen out of the sky yet");
        assertEquals(0.0f, end.yaw(), 0.01f, "and it still knows where it was going");
    }

    @Test
    @DisplayName("modes agree with themselves about whether they are still flying")
    void modeFlagsAreConsistent() {
        for (FallMode mode : FallMode.values()) {
            assertFalse(mode.isFalling() && mode.isPowered(),
                    mode.id() + " cannot be both falling and under power");
        }
        assertTrue(FallMode.NONE.isPowered());
        assertTrue(FallMode.SPIRAL.isFalling());
    }

    @Test
    @DisplayName("modes survive the trip through a byte")
    void byteRoundTrip() {
        for (FallMode mode : FallMode.values()) {
            assertEquals(mode, FallMode.fromByte(mode.toByte()));
        }
        assertEquals(FallMode.NONE, FallMode.fromByte((byte) 99), "junk decodes to something safe");
    }
}
