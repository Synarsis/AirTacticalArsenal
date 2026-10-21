package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.gui.PatriotPanelLayout.Layout;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot console layout")
class PatriotPanelLayoutTest {

    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {640, 360}, {854, 480}, {960, 540}, {1920, 1080}};

    @Test
    @DisplayName("every button sits inside the panel, buttons never overlap, and each command has its own button")
    void fits() {
        for (boolean launcher : new boolean[] {true, false}) {
            for (int[] screen : SCREENS) {
                Layout l = PatriotPanelLayout.compute(screen[0], screen[1], launcher);
                String at = (launcher ? "launcher " : "radar ") + screen[0] + "x" + screen[1];
                assertTrue(l.panel().x() >= 0 && l.panel().y() >= 0 && l.panel().right() <= screen[0]
                        && l.panel().bottom() <= screen[1], at + " panel " + l.panel());
                List<Rect> all = new ArrayList<>();
                for (Map.Entry<Command, Rect> entry : l.buttons().entrySet()) {
                    Rect r = entry.getValue();
                    assertTrue(l.panel().contains(r), at + " " + entry.getKey() + " outside the panel");
                    assertFalse(r.isEmpty(), at + " " + entry.getKey());
                    for (Rect other : all) {
                        assertFalse(r.overlaps(other), at + " " + entry.getKey() + " overlaps");
                    }
                    all.add(r);
                }
                for (Rect text : new Rect[] {l.header(), l.progress(), l.status(), l.hint()}) {
                    assertTrue(l.panel().contains(text), at + " " + text);
                    for (Rect button : all) {
                        assertFalse(text.overlaps(button), at + " " + text + " is over a button");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the launcher has the turret buttons and no hatches, the radar the opposite")
    void buttonsByVehicle() {
        Layout launcher = PatriotPanelLayout.compute(854, 480, true);
        Layout radar = PatriotPanelLayout.compute(854, 480, false);
        for (Command c : new Command[] {Command.TRAVERSE_LEFT, Command.TRAVERSE_CENTER, Command.TRAVERSE_RIGHT}) {
            assertTrue(launcher.buttons().containsKey(c));
            assertFalse(radar.buttons().containsKey(c));
        }
        for (Command c : new Command[] {Command.SERVICE_OPEN, Command.SERVICE_CLOSE}) {
            assertTrue(radar.buttons().containsKey(c));
            assertFalse(launcher.buttons().containsKey(c));
        }
        for (Command c : new Command[] {Command.DEPLOY, Command.STOW, Command.SUPPORTS_OUT, Command.SUPPORTS_IN,
                Command.LIFT_UP, Command.LIFT_DOWN, Command.DETACH, Command.ATTACH}) {
            assertTrue(launcher.buttons().containsKey(c) && radar.buttons().containsKey(c), c.toString());
        }
    }
}
