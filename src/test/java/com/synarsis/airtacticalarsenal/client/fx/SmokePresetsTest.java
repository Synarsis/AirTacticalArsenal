package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.SmokePresets.Limits;
import com.synarsis.airtacticalarsenal.client.fx.SmokePresets.Preset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Smoke motion and the presets of the scenes (SPEC 14.2, revision 12)")
class SmokePresetsTest {

    private static final double SLACK = 0.02;

    @Test
    @DisplayName("every preset over its whole life climbs and flies within the SPEC's table")
    void presetsStayWithinTheTable() {
        assertFalse(SmokePresets.all().isEmpty());
        for (Preset preset : SmokePresets.all()) {
            Limits limits = preset.limits();
            for (SmokeParams variant : preset.variants()) {
                String at = preset.name() + " (life " + variant.lifetime() + ", pixel " + variant.pixel() + ")";
                for (double lift : preset.lift()) {
                    SmokeMotion.Journey j = SmokeMotion.simulate(variant, 0.0, lift, 0.0, 0.5);
                    assertTrue(j.rise() >= limits.minRise() - SLACK, at + " climbs " + j.rise() + ", at least " + limits.minRise());
                    assertTrue(j.rise() <= limits.maxRise() + SLACK, at + " climbs " + j.rise() + ", at most " + limits.maxRise());
                    assertTrue(j.maxLift() <= limits.maxLift() + 1.0E-9, at + " rises at " + j.maxLift() + ", at most " + limits.maxLift());
                }
                double fastest = preset.lateral()[1];
                SmokeMotion.Journey sideways = SmokeMotion.simulate(variant, fastest, 0.0, 0.0, 0.5);
                assertTrue(sideways.side() <= limits.maxSide() + SLACK, at + " flies " + sideways.side() + ", at most " + limits.maxSide());
                if (fastest > 0.0) {
                    assertTrue(sideways.side() >= limits.minSide() - SLACK, at + " flies " + sideways.side() + ", at least " + limits.minSide());
                }
            }
        }
    }

    @Test
    @DisplayName("a puff is never bigger than 7 blocks across if soft, or 3 if pixelated, when it is near")
    void sizesNear() {
        for (Preset preset : SmokePresets.all()) {
            for (SmokeParams v : preset.variants()) {
                float limit = v.pixel() ? SmokePresets.PIXEL_LIMIT : SmokePresets.SOFT_LIMIT;
                assertTrue(v.size1() <= limit + 1.0E-6f, preset.name() + " grows to " + v.size1() + " across");
                assertTrue(v.size0() <= v.size1() + 1.0E-6f, preset.name() + " shrinks as it grows");
            }
        }
        SmokeParams big = SmokeParams.soft(1f, 1f, 1f).withSize(5.0f, 12.0f);
        assertEquals(7.0f, SmokePresets.limited(big).size1(), 0.0f);
        assertEquals(3.0f, SmokePresets.limited(big.withPixel(true)).size1(), 0.0f);
        assertTrue(SmokePresets.pillar(true).scaled(FxBudget.sizeMultiplier(300.0)).size1() > 7.0f);
    }

    @Test
    @DisplayName("the buoyancy worked out for a climb really gives that climb, whatever the drag and the delay")
    void buoyancyGivesTheClimb() {
        for (float drag : new float[] {0.9f, 0.95f, 0.97f, 0.985f}) {
            for (int delay : new int[] {0, 40, 80}) {
                SmokeParams p = SmokeParams.soft(1f, 1f, 1f).withLife(400).withDrift(drag, 5.0f, 0.0f).withRiseDelay(delay);
                SmokeMotion.Journey j = SmokeMotion.simulate(p, 0.0, 0.0, 0.0, 0.0);
                assertEquals(5.0, j.rise(), 0.01, "drag " + drag + " delay " + delay);
            }
        }
    }

    @Test
    @DisplayName("a constant push cannot make smoke climb for ever: it climbs a stated amount and no more")
    void noRunawayClimb() {
        SmokeParams p = SmokeParams.soft(1f, 1f, 1f).withLife(1000).withDrift(0.985f, 2.0f, 0.0f);
        SmokeMotion.Journey j = SmokeMotion.simulate(p, 0.0, 0.0, 0.0, 0.0);
        assertEquals(2.0, j.rise(), 0.01);
        assertEquals(0.0f, SmokeParams.soft(1f, 1f, 1f).buoyancy(), 0.0f, "no climb asked for, no push");
    }

    @Test
    @DisplayName("the launch cloud has no lift at all for its first 80 ticks")
    void cloudWaitsBeforeItLifts() {
        SmokeParams cloud = SmokePresets.launchCloud(new float[] {0.5f, 0.5f, 0.5f}, 1.0f, false, 400);
        SmokeMotion m = new SmokeMotion(cloud, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 80; i++) {
            m.accelerate(0.0, 0.0, 0.0, 0.0);
            assertEquals(0.0, m.vy, 0.0, "tick " + i);
        }
        m.accelerate(0.0, 0.0, 0.0, 0.0);
        assertTrue(m.vy > 0.0, "and then a little");
    }

    @Test
    @DisplayName("the column behind a rocket does not climb at all")
    void columnDoesNotClimb() {
        SmokeParams column = SmokePresets.trailColumn(0.93f, 400, 6.0f);
        assertEquals(0.0f, column.buoyancy(), 0.0f);
        assertEquals(400, column.lifetime());
        assertEquals(6.0f, column.size1(), 0.0f);
    }

    @Test
    @DisplayName("the cap of a mushroom is one push outwards when the climb has died away, and stands where it is taken to")
    void capIsOnePush() {
        SmokeParams capped = SmokePresets.cappedPillar();
        SmokeMotion m = new SmokeMotion(capped, 0.0, 0.3, 0.0, 0.0, 0.0, 0.5, 0.0);
        double x = 0.0;
        double z = 0.0;
        int pushedAt = -1;
        double speedBefore = 0.0;
        for (int i = 0; i < 400; i++) {
            m.accelerate(0.0, 0.0, x, z);
            x += m.vx;
            z += m.vz;
            if (m.capped() && pushedAt < 0) {
                pushedAt = i;
                speedBefore = Math.hypot(m.vx, m.vz);
            }
        }
        assertTrue(pushedAt > 0, "it was pushed");
        assertTrue(m.vy < SmokeMotion.CAP_BELOW, "at the moment its climb had slowed");
        assertTrue(speedBefore >= 0.06 && speedBefore <= 0.125, "the push was 0.06 to 0.12, was " + speedBefore);
        double finalSpeed = Math.hypot(m.vx, m.vz);
        assertTrue(finalSpeed < speedBefore * 0.1, "and then drag took it away: " + finalSpeed);
    }

    @Test
    @DisplayName("the cap spreads 8 to 14 blocks from the axis, not hundreds, and then stands")
    void capRadius() {
        SmokeParams capped = SmokePresets.cappedPillar();
        double typical = SmokeMotion.simulate(capped, 0.025, 0.27, 0.0, 0.5).maxSide();
        assertTrue(typical >= 8.0 && typical <= 14.0, "a typical puff reaches " + typical);
        double widest = 0.0;
        double narrowest = Double.MAX_VALUE;
        for (double lift : new double[] {0.18, 0.36}) {
            for (double sideways : new double[] {0.0, 0.05}) {
                for (double roll : new double[] {0.0, 1.0}) {
                    double side = SmokeMotion.simulate(capped, sideways, lift, 0.0, roll).maxSide();
                    widest = Math.max(widest, side);
                    narrowest = Math.min(narrowest, side);
                }
            }
        }
        assertTrue(widest <= 14.5, "the widest puff reaches " + widest);
        assertTrue(narrowest >= 4.0, "the narrowest still spreads " + narrowest);
    }

    @Test
    @DisplayName("the pillar without a cap, a launcher's, goes up half as far and does not spread")
    void pillarWithoutCap() {
        SmokeParams plain = SmokePresets.pillar(false);
        assertEquals(0.0f, plain.capPush(), 0.0f);
        double rise = SmokeMotion.simulate(plain, 0.0, 0.18, 0.0, 0.5).rise();
        double riseHigh = SmokeMotion.simulate(plain, 0.0, 0.36, 0.0, 0.5).rise();
        assertTrue(rise >= 14.0 && riseHigh <= 31.0, "15 to 30 blocks, was " + rise + " to " + riseHigh);
        assertTrue(SmokeMotion.simulate(plain, 0.05, 0.3, 0.0, 0.5).side() < 5.0);
    }

    @Test
    @DisplayName("the wind carries a puff, but only by what its share of the wind says, and it settles to the wind's speed")
    void windCarries() {
        SmokeParams p = SmokeParams.soft(1f, 1f, 1f).withLife(1000).withDrift(0.95f, 0.0f, 0.5f);
        SmokeMotion m = new SmokeMotion(p, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 500; i++) {
            m.accelerate(0.04, 0.0, 0.0, 0.0);
        }
        assertEquals(0.02, m.vx, 1.0E-6, "half of 0.04");
        SmokeParams none = SmokeParams.soft(1f, 1f, 1f).withLife(1000).withDrift(0.95f, 0.0f, 0.0f);
        SmokeMotion still = new SmokeMotion(none, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 500; i++) {
            still.accelerate(0.04, 0.0, 0.0, 0.0);
        }
        assertEquals(0.0, still.vx, 1.0E-9);
    }
}
