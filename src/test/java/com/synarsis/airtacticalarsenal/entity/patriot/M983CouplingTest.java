package com.synarsis.airtacticalarsenal.entity.patriot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.client.gui.M983HudLines;
import com.synarsis.airtacticalarsenal.entity.patriot.M983Coupling.Verdict;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity.CouplingState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("M983 coupling rules")
class M983CouplingTest {

    @Test
    void partnerPresenceAndAbsenceBranches() {
        for (boolean present : new boolean[] {false, true}) {
            for (boolean known : new boolean[] {false, true}) {
                for (boolean ticking : new boolean[] {false, true}) {
                    for (boolean destroyed : new boolean[] {false, true}) {
                        for (int ticks : new int[] {0, 1, 99, 100, 101}) {
                            M983Coupling.Partner expected = destroyed ? M983Coupling.Partner.GONE
                                    : present ? M983Coupling.Partner.HERE
                                    : known && ticking && ticks >= 100 ? M983Coupling.Partner.GONE
                                    : M983Coupling.Partner.UNLOADED;
                            assertEquals(expected, M983Coupling.partner(present, known, ticking, ticks, destroyed));
                            assertEquals(present || !known || !ticking ? 0 : Math.min(ticks + 1, 100),
                                    M983Coupling.nextAbsent(ticks, present, known, ticking));
                        }
                    }
                }
            }
        }
    }

    @Test
    void onlyConsecutiveTickingAbsenceReleases() {
        int absent = 0;
        for (int tick = 1; tick <= 99; tick++) {
            absent = M983Coupling.nextAbsent(absent, false, true, true);
            assertEquals(M983Coupling.Partner.UNLOADED, M983Coupling.partner(false, true, true, absent, false));
        }
        assertEquals(0, M983Coupling.nextAbsent(absent, false, true, false));
        assertEquals(0, M983Coupling.nextAbsent(absent, true, true, true));
        absent = M983Coupling.nextAbsent(absent, false, true, true);
        assertEquals(M983Coupling.Partner.GONE, M983Coupling.partner(false, true, true, absent, false));
    }

    @Test
    void unloadedHudAndTranslations() throws IOException {
        for (CouplingState state : new CouplingState[] {CouplingState.LOCKING, CouplingState.COUPLED,
                CouplingState.UNLOCKING}) {
            List<M983HudLines.Line> lines = M983HudLines.lines(state, null, true, false);
            assertEquals(1, lines.size());
            assertEquals("hud.ata.m983.trailer_unloaded", lines.getFirst().key());
            assertEquals(M983HudLines.Tone.YELLOW, lines.getFirst().tone());
        }
        assertEquals("hud.ata.m983.free", M983HudLines.lines(CouplingState.FREE, null, true, false).getFirst().key());
        for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
            JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources", "assets",
                    "ata", "lang", file), StandardCharsets.UTF_8)).getAsJsonObject();
            assertNotNull(lang.get("hud.ata.m983.trailer_unloaded"));
            assertNotNull(lang.get("gui.ata.patriot.tractor_unloaded"));
        }
    }

    @Test
    @DisplayName("the kingpin must be within 0.35 blocks sideways, whichever way it lies")
    void distance() {
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, 0.0, 0.0, 0.0));
        assertEquals(Verdict.READY, M983Coupling.verdict(0.34, 0.0, 0.0, 0.0));
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, 0.0, -0.34, 0.0));
        assertEquals(Verdict.READY, M983Coupling.verdict(0.24, 0.0, 0.24, 0.0));
        assertEquals(Verdict.FAR, M983Coupling.verdict(0.36, 0.0, 0.0, 0.0));
        assertEquals(Verdict.FAR, M983Coupling.verdict(0.0, 0.0, 0.36, 0.0));
        assertEquals(Verdict.FAR, M983Coupling.verdict(0.26, 0.0, 0.26, 0.0));
        assertEquals(Verdict.FAR, M983Coupling.verdict(-3.0, 0.0, 0.0, 0.0));
    }

    @Test
    @DisplayName("the heights may differ by 0.3 blocks, above or below")
    void height() {
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, 0.29, 0.0, 0.0));
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, -0.29, 0.0, 0.0));
        assertEquals(Verdict.HEIGHT, M983Coupling.verdict(0.0, 0.31, 0.0, 0.0));
        assertEquals(Verdict.HEIGHT, M983Coupling.verdict(0.0, -0.31, 0.0, 0.0));
    }

    @Test
    @DisplayName("the headings may differ by 25 degrees either way, and not when the tractor faces the trailer")
    void angle() {
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, 0.0, 0.0, 24.0));
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, 0.0, 0.0, -24.0));
        assertEquals(Verdict.ANGLE, M983Coupling.verdict(0.0, 0.0, 0.0, 26.0));
        assertEquals(Verdict.ANGLE, M983Coupling.verdict(0.0, 0.0, 0.0, -26.0));
        assertEquals(Verdict.ANGLE, M983Coupling.verdict(0.0, 0.0, 0.0, 180.0));
        assertEquals(Verdict.READY, M983Coupling.verdict(0.0, 0.0, 0.0, M983Coupling.bend(170.0f, -175.0f)));
        assertEquals(-15.0, M983Coupling.bend(170.0f, 155.0f), 1.0e-9);
        assertEquals(20.0, M983Coupling.bend(170.0f, -170.0f), 1.0e-9);
    }

    @Test
    @DisplayName("speed: 3 km/h or less, ten ticks in a row")
    void speed() {
        assertTrue(M983Coupling.slowEnough(0.0));
        assertTrue(M983Coupling.slowEnough(2.9 / 3.6));
        assertTrue(M983Coupling.slowEnough(-2.9 / 3.6));
        assertFalse(M983Coupling.slowEnough(3.1 / 3.6));
        assertFalse(M983Coupling.slowEnough(-3.1 / 3.6));
        assertFalse(M983Coupling.heldStill(9));
        assertTrue(M983Coupling.heldStill(10));
        assertTrue(M983Coupling.heldStill(30));
    }

    @Test
    @DisplayName("a trailer is free only on supports, whole, empty and idle")
    void trailerAvailable() {
        assertTrue(M983Coupling.trailerAvailable(false, false, false, false, 10.0f, true, false, false));
        assertFalse(M983Coupling.trailerAvailable(true, false, false, false, 10.0f, true, false, false));
        assertFalse(M983Coupling.trailerAvailable(false, true, false, false, 10.0f, true, false, false));
        assertFalse(M983Coupling.trailerAvailable(false, false, true, false, 10.0f, true, false, false));
        assertFalse(M983Coupling.trailerAvailable(false, false, false, true, 10.0f, true, false, false));
        assertFalse(M983Coupling.trailerAvailable(false, false, false, false, 0.0f, true, false, false));
        assertFalse(M983Coupling.trailerAvailable(false, false, false, false, 10.0f, false, false, false));
        assertFalse(M983Coupling.trailerAvailable(false, false, false, false, 10.0f, true, true, false));
        assertFalse(M983Coupling.trailerAvailable(false, false, false, false, 10.0f, true, false, true));
    }

    @Test
    @DisplayName("uncoupling needs a coupled trailer, a standing tractor and the supports out, and says which is missing")
    void uncouple() throws IOException {
        assertEquals(M983Coupling.Uncouple.OK, M983Coupling.uncouple(true, true, false, 0.0));
        assertEquals(M983Coupling.Uncouple.NOT_COUPLED, M983Coupling.uncouple(false, true, false, 0.0));
        assertEquals(M983Coupling.Uncouple.SUPPORTS, M983Coupling.uncouple(true, false, false, 0.0));
        assertEquals(M983Coupling.Uncouple.SUPPORTS, M983Coupling.uncouple(true, true, true, 0.0));
        assertEquals(M983Coupling.Uncouple.MOVING, M983Coupling.uncouple(true, true, false, 1.0));
        assertEquals(M983Coupling.Uncouple.MOVING, M983Coupling.uncouple(true, true, false, -1.0));
        assertEquals(M983Coupling.Uncouple.MOVING, M983Coupling.uncouple(true, false, false, 2.0));
        for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
            Path path = Path.of("src", "main", "resources", "assets", "ata", "lang", file);
            JsonObject lang = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (M983Coupling.Uncouple each : M983Coupling.Uncouple.values()) {
                if (each.panelKey() != null) {
                    assertNotNull(lang.get(each.panelKey()), file + " lacks " + each.panelKey());
                }
                if (each.messageKey() != null) {
                    assertNotNull(lang.get(each.messageKey()), file + " lacks " + each.messageKey());
                }
            }
            for (String key : new String[] {"gui.ata.m983.trailer_panel", "gui.ata.m983.trailer_panel.why",
                    "gui.ata.m983.coupling.locking", "gui.ata.m983.coupling.unlocking", "gui.ata.m983.coupling.coupled"}) {
                assertNotNull(lang.get(key), file + " lacks " + key);
            }
        }
    }

    @Test
    @DisplayName("the trailer pose put the kingpin exactly on the saddle, for any bend, pitch and roll")
    void poseMeetsTheSaddle() {
        float[][] poses = {{0.0f, 0.0f, 0.0f}, {37.0f, 4.0f, -3.0f}, {-120.0f, -7.0f, 5.0f}, {179.0f, 0.0f, 0.0f}};
        double[] bends = {0.0, 20.0, -35.0, 60.0};
        for (float[] pose : poses) {
            for (double bend : bends) {
                double[] trailer = M983Coupling.trailerPoseFor(10.5, 64.0, -20.25, pose[0], pose[1], pose[2], bend);
                Vec3 saddle = M983Coupling.worldOf(10.5, 64.0, -20.25, pose[0], pose[1], pose[2],
                        M983Coupling.saddleAnchor());
                Vec3 kingpin = M983Coupling.worldOf(trailer[0], trailer[1], trailer[2], (float) trailer[3],
                        (float) trailer[4], (float) trailer[5], M983Coupling.kingpinAnchor());
                assertEquals(0.0, saddle.distanceTo(kingpin), 1.0e-6,
                        "pose " + pose[0] + " " + pose[1] + " " + pose[2] + " bend " + bend);
                assertEquals(bend, M983Coupling.bend(pose[0], (float) trailer[3]), 1.0e-4);
            }
        }
    }

    @Test
    @DisplayName("the HUD says what the driver needs at each stage, and every line has text in both languages")
    void hudLines() throws IOException {
        M983Coupling.Hint close = new M983Coupling.Hint(1, 2.4, 0.3, 0.0, 6.0, Verdict.FAR, false);
        List<M983HudLines.Line> far = M983HudLines.lines(CouplingState.FREE, close, true);
        assertEquals(1, far.size());
        assertEquals("hud.ata.m983.hint.left", far.get(0).key());
        assertEquals("2.4", far.get(0).args()[0]);
        assertEquals("0.3", far.get(0).args()[1]);
        assertEquals("6", far.get(0).args()[2]);
        assertEquals("hud.ata.m983.hint.right", M983HudLines.lines(CouplingState.FREE,
                new M983Coupling.Hint(1, 2.4, -0.3, 0.0, 6.0, Verdict.FAR, false), true).get(0).key());
        assertEquals("hud.ata.m983.hint.centre", M983HudLines.lines(CouplingState.FREE,
                new M983Coupling.Hint(1, 2.4, 0.02, 0.0, 6.0, Verdict.FAR, false), true).get(0).key());
        M983Coupling.Hint ready = new M983Coupling.Hint(1, 0.1, 0.0, 0.0, 2.0, Verdict.READY, true);
        assertEquals("hud.ata.m983.ready", M983HudLines.lines(CouplingState.FREE, ready, true).get(1).key());
        assertEquals("hud.ata.m983.stop", M983HudLines.lines(CouplingState.FREE, ready, false).get(1).key());
        assertEquals("hud.ata.m983.free", M983HudLines.lines(CouplingState.FREE, null, true).get(0).key());
        assertEquals("hud.ata.m983.locking", M983HudLines.lines(CouplingState.LOCKING, null, true).get(0).key());
        assertEquals("hud.ata.m983.coupled", M983HudLines.lines(CouplingState.COUPLED, null, true).get(0).key());
        assertEquals("hud.ata.m983.unlocking", M983HudLines.lines(CouplingState.UNLOCKING, null, true).get(0).key());
        for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
            Path path = Path.of("src", "main", "resources", "assets", "ata", "lang", file);
            JsonObject lang = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : new String[] {"hud.ata.m983.hint.left", "hud.ata.m983.hint.right",
                    "hud.ata.m983.hint.centre", "hud.ata.m983.ready", "hud.ata.m983.stop", "hud.ata.m983.locking",
                    "hud.ata.m983.coupled", "hud.ata.m983.unlocking", "hud.ata.m983.free",
                    "message.ata.m983.coupled", "message.ata.m983.uncoupled", "message.ata.m983.unlock.supports",
                    "gui.ata.m983.unlock.supports", "gui.ata.m983.unlock.moving"}) {
                assertNotNull(lang.get(key), file + " lacks " + key);
            }
        }
    }
}
