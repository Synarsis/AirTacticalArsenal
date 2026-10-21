package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Card;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Layout;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Console layout")
class TelPanelLayoutTest {

    private static Map<String, Rect> leaves(Layout l) {
        Map<String, Rect> m = new LinkedHashMap<>();
        m.put("title", l.title());
        m.put("chip", l.chip());
        m.put("progress", l.progress());
        for (int i = 0; i < 2; i++) {
            Card c = l.cards()[i];
            m.put("card" + i + ".title", c.title());
            m.put("card" + i + ".status", c.status());
            m.put("card" + i + ".x", c.xField());
            m.put("card" + i + ".z", c.zField());
            m.put("card" + i + ".ok", c.ok());
            m.put("card" + i + ".info", c.info());
            m.put("card" + i + ".error", c.error());
            m.put("card" + i + ".load", c.load());
            m.put("card" + i + ".unload", c.unload());
            m.put("card" + i + ".prepare", c.prepare());
        }
        m.put("copy", l.copy());
        m.put("tablet", l.tablet());
        m.put("salvo.label", l.salvoLabel());
        m.put("salvo.minus", l.minus());
        m.put("salvo.value", l.value());
        m.put("salvo.plus", l.plus());
        m.put("salvo.both", l.both());
        for (int i = 0; i < l.bottom().length; i++) {
            m.put("bottom" + i, l.bottom()[i]);
        }
        Rect label = TelPanelLayout.checksLabel(l);
        m.put("checks.label", label);
        for (int i = 0; i < TelPanelLayout.CHECK_COUNT; i++) {
            if (l.checkVisible(i, 0)) {
                m.put("check" + i, l.checkCell(i, 0));
            }
        }
        return m;
    }

    private static void assertSound(int w, int h) {
        Layout l = TelPanelLayout.compute(w, h);
        String at = " at " + w + "x" + h + (l.compact() ? " (compact)" : " (full)");

        assertTrue(new Rect(0, 0, w, h).contains(l.panel()), "panel on the screen" + at);

        Map<String, Rect> leaves = leaves(l);
        for (Map.Entry<String, Rect> e : leaves.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            assertTrue(l.panel().contains(e.getValue()), e.getKey() + " leaves the panel" + at + ": " + e.getValue());
        }
        assertTrue(l.panel().contains(l.checksView()), "checks window in the panel" + at);

        List<String> names = new ArrayList<>(leaves.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                Rect a = leaves.get(names.get(i));
                Rect b = leaves.get(names.get(j));
                assertFalse(a.overlaps(b), names.get(i) + " " + a + " lies on " + names.get(j) + " " + b + at);
            }
        }

        for (int i = 0; i < 2; i++) {
            Card c = l.cards()[i];
            for (Rect r : new Rect[] {c.title(), c.status(), c.xField(), c.zField(), c.ok(), c.info(), c.error(),
                    c.load(), c.unload(), c.prepare()}) {
                assertTrue(c.box().contains(r), "card " + i + " child " + r + " outside " + c.box() + at);
            }
            assertTrue(l.panel().contains(c.box()), "card " + i + " in the panel" + at);
            assertFalse(c.box().overlaps(l.salvoBox()), "card over the salvo row" + at);
            assertFalse(c.box().overlaps(l.checksView()), "card over the checks" + at);
            for (Rect b : l.bottom()) {
                assertFalse(c.box().overlaps(b), "card over the bottom row" + at);
            }
        }
        assertFalse(l.cards()[0].box().overlaps(l.cards()[1].box()), "cards overlap" + at);
        assertFalse(l.salvoBox().overlaps(l.checksView()), "salvo row over the checks" + at);
        assertFalse(l.header().overlaps(l.cards()[0].box()), "header over a card" + at);
        for (Rect b : l.bottom()) {
            assertFalse(b.overlaps(l.checksView()), "bottom row over the checks" + at);
            assertFalse(b.overlaps(l.salvoBox()), "bottom row over the salvo row" + at);
        }
        assertFalse(l.salvoBox().overlaps(l.header()));
        assertTrue(l.salvoBox().contains(l.both()) && l.salvoBox().contains(l.tablet()) && l.salvoBox().contains(l.plus()));

        assertTrue(l.chip().w() >= 90, "chip" + at);
        assertTrue(l.title().w() >= 100, "title" + at);
        for (Rect b : l.bottom()) {
            assertTrue(b.w() >= 60, "bottom button too narrow: " + b + at);
        }
        for (int i = 0; i < 2; i++) {
            assertTrue(l.cards()[i].xField().w() >= 36, "field too narrow" + at);
            assertTrue(l.cards()[i].prepare().w() >= 50 && l.cards()[i].load().w() >= 50, "card buttons" + at);
        }
    }

    @Test
    @DisplayName("at 640x360 the full arrangement fits")
    void full() {
        assertSound(640, 360);
        assertFalse(TelPanelLayout.compute(640, 360).compact());
    }

    @Test
    @DisplayName("at 480x270 the compact one fits")
    void middling() {
        assertSound(480, 270);
        assertTrue(TelPanelLayout.compute(480, 270).compact());
    }

    @Test
    @DisplayName("at 427x240 the compact one fits")
    void small() {
        assertSound(427, 240);
        assertTrue(TelPanelLayout.compute(427, 240).compact());
    }

    @Test
    @DisplayName("the other sizes players have fit too")
    void others() {
        for (int[] size : new int[][] {{854, 480}, {960, 540}, {1280, 720}, {1920, 1080}, {512, 288}, {400, 300},
                {640, 300}, {460, 300}, {800, 280}}) {
            assertSound(size[0], size[1]);
        }
    }

    @Test
    @DisplayName("every check is visible where there is room, and the rest can be scrolled to")
    void checks() {
        for (int[] size : new int[][] {{640, 360}, {480, 270}, {427, 240}}) {
            Layout l = TelPanelLayout.compute(size[0], size[1]);
            int shown = 0;
            for (int i = 0; i < TelPanelLayout.CHECK_COUNT; i++) {
                if (l.checkVisible(i, 0)) {
                    shown++;
                }
            }
            assertTrue(shown > 0);
            if (l.maxScroll() == 0) {
                assertEquals(TelPanelLayout.CHECK_COUNT, shown, "nothing to scroll to, so all must show at " + size[0]);
            } else {
                int last = TelPanelLayout.CHECK_COUNT - 1;
                assertTrue(l.checkVisible(last, l.maxScroll()), "scrolled to the end shows the last check");
            }
        }
        Layout tiny = TelPanelLayout.compute(427, 200);
        assertTrue(tiny.maxScroll() > 0 || tiny.checksView().h() >= tiny.checksContentHeight());
        assertTrue(tiny.panel().contains(tiny.checksView()));
    }

    @Test
    @DisplayName("the launch button is the widest of the bottom row")
    void launchIsBig() {
        Layout l = TelPanelLayout.compute(640, 360);
        Rect launch = l.bottom()[4];
        for (int i = 0; i < 4; i++) {
            assertTrue(launch.w() > l.bottom()[i].w());
        }
    }
}
