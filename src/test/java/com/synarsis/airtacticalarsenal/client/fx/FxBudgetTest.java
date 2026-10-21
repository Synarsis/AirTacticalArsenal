package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.config.FxQuality;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Particle budget and distance levels")
class FxBudgetTest {

    @AfterEach
    void clean() {
        FxBudget.reset();
    }

    @Test
    @DisplayName("the count falls with distance: all up to 64 blocks, 40% to 192, 15% to 512, nothing beyond")
    void levels() {
        assertEquals(1.0, FxBudget.countMultiplier(0.0), 0.0);
        assertEquals(1.0, FxBudget.countMultiplier(63.9), 0.0);
        assertEquals(0.4, FxBudget.countMultiplier(64.0), 0.0);
        assertEquals(0.4, FxBudget.countMultiplier(191.0), 0.0);
        assertEquals(0.15, FxBudget.countMultiplier(300.0), 0.0);
        assertEquals(0.15, FxBudget.countMultiplier(511.0), 0.0);
        assertEquals(0.0, FxBudget.countMultiplier(512.0), 0.0);
        assertEquals(0.0, FxBudget.countMultiplier(2000.0), 0.0);
    }

    @Test
    @DisplayName("sizes grow to make up for it: 1, 1.6, 2.5, 3")
    void sizes() {
        assertEquals(1.0, FxBudget.sizeMultiplier(10.0), 0.0);
        assertEquals(1.6, FxBudget.sizeMultiplier(100.0), 0.0);
        assertEquals(2.5, FxBudget.sizeMultiplier(300.0), 0.0);
        assertEquals(3.0, FxBudget.sizeMultiplier(600.0), 0.0);
    }

    @Test
    @DisplayName("at 300 blocks 100 requested puffs become 15")
    void threeHundredBlocks() {
        assertEquals(15, FxBudget.allow(100, 300.0, false, FxQuality.HIGH, 1.0, 0, 0.0));
        assertEquals(40, FxBudget.allow(100, 100.0, false, FxQuality.HIGH, 1.0, 0, 0.0));
        assertEquals(100, FxBudget.allow(100, 10.0, false, FxQuality.HIGH, 1.0, 0, 0.0));
    }

    @Test
    @DisplayName("nothing is made once the cap is reached, however near")
    void overCap() {
        assertEquals(0, FxBudget.allow(100, 10.0, false, FxQuality.HIGH, 1.0, FxBudget.CAP, 0.99));
        assertEquals(0, FxBudget.allow(100, 10.0, false, FxQuality.HIGH, 1.0, FxBudget.CAP + 50, 0.99));
        assertEquals(30, FxBudget.allow(100, 10.0, false, FxQuality.HIGH, 1.0, FxBudget.CAP - 30, 0.0),
                "only what fits under the cap");
    }

    @Test
    @DisplayName("the column of smoke and the trail may go on a little past the cap, and are still drawn far away")
    void essentialRoom() {
        assertTrue(FxBudget.allow(10, 10.0, true, FxQuality.HIGH, 1.0, FxBudget.CAP + 100, 0.0) > 0);
        assertEquals(0, FxBudget.allow(10, 10.0, true, FxQuality.HIGH, 1.0, FxBudget.CAP + FxBudget.ESSENTIAL_ROOM, 0.0));
        assertEquals(0, FxBudget.allow(100, 700.0, false, FxQuality.HIGH, 1.0, 0, 0.99), "ordinary effects stop at 512");
        assertTrue(FxBudget.allow(100, 700.0, true, FxQuality.HIGH, 1.0, 0, 0.0) > 0, "the column does not");
    }

    @Test
    @DisplayName("the quality setting scales the count: low 35%, medium 65%, high all of it")
    void quality() {
        assertEquals(35, FxBudget.allow(100, 0.0, false, FxQuality.LOW, 1.0, 0, 0.0));
        assertEquals(65, FxBudget.allow(100, 0.0, false, FxQuality.MEDIUM, 1.0, 0, 0.0));
        assertEquals(100, FxBudget.allow(100, 0.0, false, FxQuality.HIGH, 1.0, 0, 0.0));
        assertEquals(50, FxBudget.allow(100, 0.0, false, FxQuality.HIGH, 0.5, 0, 0.0), "and the server's own multiplier");
        assertEquals(0, FxBudget.allow(100, 0.0, false, FxQuality.HIGH, 0.0, 0, 0.99), "switched off");
    }

    @Test
    @DisplayName("a fraction is rounded by the roll, so that on average the right number is made")
    void rounding() {
        int made = 0;
        for (int i = 0; i < 1000; i++) {
            made += FxBudget.allow(1, 300.0, false, FxQuality.HIGH, 1.0, 0, i / 1000.0);
        }
        assertEquals(150, made, 2);
    }

    @Test
    @DisplayName("the live count goes up and down, never below zero, and resets with the world")
    void counting() {
        FxBudget.born();
        FxBudget.born();
        assertEquals(2, FxBudget.live());
        FxBudget.died();
        FxBudget.died();
        FxBudget.died();
        assertEquals(0, FxBudget.live());
        FxBudget.born();
        FxBudget.reset();
        assertEquals(0, FxBudget.live());
    }

    @Test
    @DisplayName("the pressure eases off as the cap nears and never reaches zero")
    void pressure() {
        assertEquals(1.0, FxBudget.pressure(0), 0.0);
        assertEquals(1.0, FxBudget.pressure((int) (FxBudget.CAP * 0.7)), 1.0E-9);
        assertEquals(0.1, FxBudget.pressure(FxBudget.CAP), 1.0E-9);
        assertEquals(0.1, FxBudget.pressure(FxBudget.CAP * 3), 1.0E-9);
        double last = 1.0;
        for (int n = 0; n <= FxBudget.CAP; n += 50) {
            double p = FxBudget.pressure(n);
            assertTrue(p <= last + 1.0E-12);
            last = p;
        }
    }
}
