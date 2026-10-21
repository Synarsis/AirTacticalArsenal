package com.synarsis.airtacticalarsenal.client.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Launch moment")
class LaunchTriggerTest {

    private static int run(LaunchTrigger t, int from, boolean goneAtEnd, long start) {
        int seen = 0;
        long now = start;
        for (int left = from; left >= 0; left--) {
            if (t.observe(left, true, now++)) {
                seen++;
            }
        }
        if (t.observe(-1, !goneAtEnd, now)) {
            seen++;
        }
        return seen;
    }

    @Test
    @DisplayName("a countdown that runs out with the round gone is one launch, seen on the tick it ends")
    void oneLaunch() {
        LaunchTrigger t = new LaunchTrigger();
        for (int left = 140; left >= 0; left--) {
            assertFalse(t.observe(left, true, 1000 + (140 - left)), "while it counts down, at " + left);
        }
        assertTrue(t.observe(-1, false, 1141), "the round has gone and the countdown with it");
        assertFalse(t.observe(-1, false, 1142), "and only once");
    }

    @Test
    @DisplayName("the countdown being called off with the round still aboard is no launch")
    void aborted() {
        LaunchTrigger t = new LaunchTrigger();
        assertEquals(0, run(t, 60, false, 0));
        LaunchTrigger late = new LaunchTrigger();
        late.observe(2, true, 10);
        assertFalse(late.observe(-1, true, 11));
    }

    @Test
    @DisplayName("a countdown called off long before its end is no launch even if the round is gone")
    void abortedEarlyAndUnloaded() {
        LaunchTrigger t = new LaunchTrigger();
        t.observe(80, true, 10);
        assertFalse(t.observe(-1, false, 11), "it was nowhere near ending");
    }

    @Test
    @DisplayName("a round kept back at its moment (sky blocked) stays aboard and is no launch")
    void keptBack() {
        LaunchTrigger t = new LaunchTrigger();
        assertEquals(0, run(t, 140, false, 0));
    }

    @Test
    @DisplayName("a missile seen at the foot after the countdown was missed is a launch, once")
    void secondSign() {
        LaunchTrigger t = new LaunchTrigger();
        assertTrue(t.missileSeen(500));
        assertFalse(t.missileSeen(501));
        assertFalse(t.missileSeen(550));
        assertTrue(t.missileSeen(700), "a later launch of the same round counts again");
    }

    @Test
    @DisplayName("both signs for one launch make one launch, whichever comes first")
    void bothSigns() {
        LaunchTrigger a = new LaunchTrigger();
        a.observe(1, true, 100);
        assertTrue(a.observe(-1, false, 101));
        assertFalse(a.missileSeen(102));

        LaunchTrigger b = new LaunchTrigger();
        b.observe(1, true, 100);
        assertTrue(b.missileSeen(101));
        assertFalse(b.observe(-1, false, 102));
    }

    @Test
    @DisplayName("the second round of a salvo has its own moment: ten seconds on, a second launch")
    void salvo() {
        LaunchTrigger first = new LaunchTrigger();
        LaunchTrigger second = new LaunchTrigger();
        int seenFirst = 0;
        int seenSecond = 0;
        for (int tick = 0; tick <= 300; tick++) {
            int leftFirst = tick < 140 ? 140 - tick : -1;
            int leftSecond = tick < 240 ? 240 - tick : -1;
            if (first.observe(leftFirst, tick < 140, tick)) {
                seenFirst++;
            }
            if (second.observe(leftSecond, tick < 240, tick)) {
                seenSecond++;
            }
        }
        assertEquals(1, seenFirst);
        assertEquals(1, seenSecond);
    }
}
