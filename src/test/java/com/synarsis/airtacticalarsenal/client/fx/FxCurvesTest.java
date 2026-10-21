package com.synarsis.airtacticalarsenal.client.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Effect curves")
class FxCurvesTest {

    @Test
    @DisplayName("smoothstep is 0 at 0, 1 at 1, a half at the middle, and rises monotonically without overshoot")
    void smoothstep() {
        assertEquals(0.0, FxCurves.smoothstep(0.0), 1.0E-12);
        assertEquals(1.0, FxCurves.smoothstep(1.0), 1.0E-12);
        assertEquals(0.5, FxCurves.smoothstep(0.5), 1.0E-12);
        assertEquals(0.0, FxCurves.smoothstep(-3.0), 1.0E-12);
        assertEquals(1.0, FxCurves.smoothstep(7.0), 1.0E-12);
        double last = 0.0;
        for (int i = 0; i <= 100; i++) {
            double v = FxCurves.smoothstep(i / 100.0);
            assertTrue(v >= last - 1.0E-12 && v <= 1.0 + 1.0E-12, "at " + i);
            last = v;
        }
        assertTrue(FxCurves.smoothstep(0.05) < 0.01);
        assertTrue(FxCurves.smoothstep(0.95) > 0.99);
    }

    @Test
    @DisplayName("a puff stays its first size until it starts to grow and its last size once it has")
    void smokeSize() {
        assertEquals(3.0, FxCurves.smokeSize(3.0, 12.0, 0, 0, 60), 1.0E-9);
        assertEquals(3.0, FxCurves.smokeSize(3.0, 12.0, 10, 20, 40), 1.0E-9, "before the growth begins");
        assertEquals(7.5, FxCurves.smokeSize(3.0, 12.0, 30, 0, 60), 1.0E-9, "half way is half the growth");
        assertEquals(12.0, FxCurves.smokeSize(3.0, 12.0, 60, 0, 60), 1.0E-9);
        assertEquals(12.0, FxCurves.smokeSize(3.0, 12.0, 500, 0, 60), 1.0E-9);
        assertEquals(12.0, FxCurves.smokeSize(3.0, 12.0, 0, 0, 0), 1.0E-9, "no growth time means full size at once");
    }

    @Test
    @DisplayName("a puff appears over three ticks, holds, and dissolves over the last 30% of its life")
    void smokeAlpha() {
        assertEquals(0.0, FxCurves.smokeAlpha(0, 100), 1.0E-9);
        assertEquals(1.0 / 3.0, FxCurves.smokeAlpha(1, 100), 1.0E-9);
        assertEquals(1.0, FxCurves.smokeAlpha(3, 100), 1.0E-9);
        assertEquals(1.0, FxCurves.smokeAlpha(50, 100), 1.0E-9);
        assertEquals(1.0, FxCurves.smokeAlpha(70, 100), 1.0E-9, "dissolving begins at 70%");
        assertEquals(0.5, FxCurves.smokeAlpha(85, 100), 1.0E-9);
        assertEquals(0.0, FxCurves.smokeAlpha(100, 100), 1.0E-9);
        for (int age = 70; age < 100; age++) {
            assertTrue(FxCurves.smokeAlpha(age + 1, 100) <= FxCurves.smokeAlpha(age, 100) + 1.0E-12);
        }
    }

    @Test
    @DisplayName("a shake is 6 degrees at most, weaker with distance, and dies away with a 0.6 second time constant")
    void shake() {
        assertEquals(6.0, FxCurves.shakeAmplitude(1.0, 0.0), 1.0E-9);
        assertEquals(6.0, FxCurves.shakeAmplitude(5.0, 0.0), 1.0E-9, "never above the ceiling");
        assertEquals(3.0, FxCurves.shakeAmplitude(1.0, 24.0), 1.0E-9);
        assertEquals(3.6, FxCurves.shakeAmplitude(0.6, 0.0), 1.0E-9);
        assertTrue(FxCurves.shakeAmplitude(1.0, 300.0) < 0.5);
        assertEquals(0.0, FxCurves.shakeAmplitude(0.0, 10.0), 1.0E-9);

        assertEquals(4.0, FxCurves.shakeAt(4.0, 0.0), 1.0E-9);
        assertEquals(4.0 / Math.E, FxCurves.shakeAt(4.0, 0.6), 1.0E-9);
        assertEquals(0.0, FxCurves.shakeAt(4.0, -1.0), 1.0E-9, "not begun");
        assertTrue(FxCurves.shakeAt(6.0, 3.0) < 0.05, "three seconds on it has all but gone");
    }

    @Test
    @DisplayName("the shock of a blast arrives at the speed of sound, 17 blocks a tick")
    void shockDelay() {
        assertEquals(0, FxCurves.shockDelayTicks(0.0));
        assertEquals(0, FxCurves.shockDelayTicks(16.0));
        assertEquals(1, FxCurves.shockDelayTicks(17.0));
        assertEquals(29, FxCurves.shockDelayTicks(500.0));
    }

    @Test
    @DisplayName("a flash is strongest facing it, weaker from behind, falls with distance and does not go round corners")
    void flash() {
        double facing = FxCurves.flashStrength(1.0, true, 1.0, 0.0);
        double side = FxCurves.flashStrength(1.0, true, 0.0, 0.0);
        double behind = FxCurves.flashStrength(1.0, true, -1.0, 0.0);
        assertEquals(1.0, facing, 1.0E-9);
        assertEquals(0.5, side, 1.0E-9);
        assertEquals(0.0, behind, 1.0E-9);
        assertEquals(0.5, FxCurves.flashStrength(1.0, true, 1.0, 80.0), 1.0E-9, "at 80 blocks it is halved");
        assertTrue(FxCurves.flashStrength(1.0, true, 1.0, 400.0) < 0.05);
        assertEquals(0.0, FxCurves.flashStrength(1.0, false, 1.0, 5.0), 1.0E-9, "a wall in between puts it out");
        assertEquals(0.5, FxCurves.flashStrength(0.5, true, 1.0, 0.0), 1.0E-9, "scales with the power");
        assertEquals(1.0, FxCurves.flashStrength(1.0, true, 7.0, 0.0), 1.0E-9);
    }

    @Test
    @DisplayName("fire goes from white-yellow through orange to dark red")
    void fireColour() {
        float[] young = FxCurves.fireColour(0.0);
        float[] mid = FxCurves.fireColour(0.6);
        float[] old = FxCurves.fireColour(1.0);
        assertTrue(young[0] > 0.95f && young[1] > 0.95f && young[2] > 0.7f, "white-yellow");
        assertTrue(mid[0] > 0.95f && mid[1] > 0.3f && mid[1] < 0.5f && mid[2] < 0.15f, "orange");
        assertTrue(old[0] < 0.6f && old[0] > 0.3f && old[1] < 0.15f, "dark red");
        float last = 2.0f;
        for (int i = 0; i <= 100; i++) {
            float g = FxCurves.fireColour(i / 100.0)[1];
            assertTrue(g <= last + 1.0E-6f, "at " + i);
            last = g;
        }
    }

    @Test
    @DisplayName("the flame shows its four frames in order over its life")
    void fireFrames() {
        assertEquals(0, FxCurves.fireFrame(0, 8, 4));
        assertEquals(1, FxCurves.fireFrame(2, 8, 4));
        assertEquals(2, FxCurves.fireFrame(4, 8, 4));
        assertEquals(3, FxCurves.fireFrame(6, 8, 4));
        assertEquals(3, FxCurves.fireFrame(99, 8, 4), "past its life it keeps the last frame");
        assertEquals(0, FxCurves.fireFrame(3, 8, 1));
    }
}
