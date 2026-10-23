package com.synarsis.airtacticalarsenal.client.hud;

import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The launcher and the tractor share one speedometer place")
class TelHudLayoutTest {

    private static final int[][] SCREENS = {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}, {427, 240}};

    @Test
    @DisplayName("same left edge, width and bottom edge whatever the content height")
    void sameAnchor() {
        for (int[] screen : SCREENS) {
            Rect launcher = TelHudLayout.panel(screen[0], screen[1], 107);
            Rect tractor = TelHudLayout.panel(screen[0], screen[1], 72);
            assertEquals(launcher.x(), tractor.x());
            assertEquals(launcher.w(), tractor.w());
            assertEquals(launcher.bottom(), tractor.bottom());
        }
    }

    @Test
    @DisplayName("it sits in the lower left, clear of the hotbar")
    void clearOfTheHotbar() {
        for (int[] screen : SCREENS) {
            Rect panel = TelHudLayout.panel(screen[0], screen[1], 90);
            assertEquals(TelHudLayout.MARGIN, panel.x());
            boolean overlapsHotbarColumns = panel.right() > screen[0] / 2 - TelHudLayout.HOTBAR_HALF;
            if (overlapsHotbarColumns) {
                assertTrue(panel.bottom() <= screen[1] - TelHudLayout.HOTBAR_HEIGHT - TelHudLayout.MARGIN,
                        panel + " overlaps the hotbar on " + screen[0] + "x" + screen[1]);
            } else {
                assertEquals(screen[1] - TelHudLayout.MARGIN, panel.bottom());
            }
        }
    }
}
