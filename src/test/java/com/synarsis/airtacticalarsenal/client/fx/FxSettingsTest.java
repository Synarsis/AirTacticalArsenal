package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.config.FxLighting;
import com.synarsis.airtacticalarsenal.config.FxQuality;
import com.synarsis.airtacticalarsenal.entity.tel.TelHeadlights;
import com.synarsis.airtacticalarsenal.network.FxDebugKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Settings of the effects, the flare's weakening, headlights, debug scenes")
class FxSettingsTest {

    @Test
    @DisplayName("the vanilla Particles setting leaves 1, 0.5 and 0.2 of the count, and the pillar and trail still get some")
    void vanillaParticleSetting() {
        assertEquals(1.0, FxBudget.vanillaShare(0), 0.0);
        assertEquals(0.5, FxBudget.vanillaShare(1), 0.0);
        assertEquals(0.2, FxBudget.vanillaShare(2), 0.0);
        assertEquals(0.2, FxBudget.vanillaShare(7), 0.0, "anything beyond is the minimum");

        assertEquals(20, FxBudget.allow(100, 10.0, false, FxQuality.HIGH, FxBudget.vanillaShare(2), 0, 0.0));
        assertEquals(50, FxBudget.allow(100, 10.0, false, FxQuality.HIGH, FxBudget.vanillaShare(1), 0, 0.0));
        assertTrue(FxBudget.allow(100, 10.0, true, FxQuality.LOW, FxBudget.vanillaShare(2), 0, 0.0) > 0,
                "the pillar thins out at Minimal");
        int far = 0;
        for (int i = 0; i < 100; i++) {
            far += FxBudget.allow(30, 700.0, true, FxQuality.LOW, FxBudget.vanillaShare(2), 0, i / 100.0);
        }
        assertTrue(far > 0, "and is still there at 700 blocks");
    }

    @Test
    @DisplayName("a rocket's flare keeps all its strength to 20 blocks up and a third of it from 120, falling evenly between")
    void flareScale() {
        assertEquals(1.0, FxCurves.flareScale(0.0), 1.0E-12);
        assertEquals(1.0, FxCurves.flareScale(20.0), 1.0E-12);
        assertEquals(0.65, FxCurves.flareScale(70.0), 1.0E-12);
        assertEquals(0.3, FxCurves.flareScale(120.0), 1.0E-12);
        assertEquals(0.3, FxCurves.flareScale(900.0), 1.0E-12);
        double last = 1.0;
        for (int h = 0; h <= 200; h += 5) {
            double s = FxCurves.flareScale(h);
            assertTrue(s <= last + 1.0E-12);
            last = s;
        }
    }

    @Test
    @DisplayName("each lighting setting adds to the one before: 8, 16, 32 lights; normals; shadows and haze only at the top")
    void lightingSettings() {
        assertEquals(0, FxLighting.OFF.maxLights());
        assertEquals(8, FxLighting.LOW.maxLights());
        assertEquals(16, FxLighting.MEDIUM.maxLights());
        assertEquals(32, FxLighting.HIGH.maxLights());
        assertTrue(FxLighting.LOW.halfResolution() && FxLighting.MEDIUM.halfResolution());
        assertFalse(FxLighting.HIGH.halfResolution());
        assertFalse(FxLighting.LOW.normals());
        assertTrue(FxLighting.MEDIUM.normals() && FxLighting.HIGH.normals());
        assertFalse(FxLighting.LOW.shadows() || FxLighting.MEDIUM.shadows());
        assertTrue(FxLighting.HIGH.shadows());
        assertFalse(FxLighting.LOW.haze() || FxLighting.MEDIUM.haze());
        assertTrue(FxLighting.HIGH.haze());
        assertEquals(0, FxLighting.OFF.blockShadowLights());
        assertEquals(2, FxLighting.LOW.blockShadowLights());
        assertEquals(4, FxLighting.MEDIUM.blockShadowLights());
        assertEquals(8, FxLighting.HIGH.blockShadowLights());
        assertEquals(1, FxLighting.LOW.shadowRays());
        assertEquals(1, FxLighting.MEDIUM.shadowRays());
        assertEquals(2, FxLighting.HIGH.shadowRays());
    }

    @Test
    @DisplayName("the key switches the headlights on and off, but never on for a machine that is wrecked or burning")
    void headlightsToggle() {
        assertTrue(TelHeadlights.toggled(false, true));
        assertFalse(TelHeadlights.toggled(true, true));
        assertFalse(TelHeadlights.toggled(false, false), "a dead machine cannot have them switched on");
        assertFalse(TelHeadlights.toggled(true, false));
    }

    @Test
    @DisplayName("a machine works when it is alive and above the burning line; a wreck and a burning hull have their lights out")
    void headlightsNeedAWorkingMachine() {
        assertTrue(TelHeadlights.works(400.0f, 400.0f, false, 0.1f));
        assertTrue(TelHeadlights.works(41.0f, 400.0f, false, 0.1f));
        assertFalse(TelHeadlights.works(40.0f, 400.0f, false, 0.1f), "at a tenth it is burning");
        assertFalse(TelHeadlights.works(10.0f, 400.0f, false, 0.1f));
        assertFalse(TelHeadlights.works(0.0f, 400.0f, false, 0.1f));
        assertFalse(TelHeadlights.works(400.0f, 400.0f, true, 0.1f), "destroyed, whatever the health says");

        assertTrue(TelHeadlights.afterTick(true, true));
        assertFalse(TelHeadlights.afterTick(true, false), "put out when the machine stops working");
        assertFalse(TelHeadlights.afterTick(false, true), "and a tick never switches them on");
    }

    @Test
    @DisplayName("a wrecked launcher cannot have the lights on by any road: the key, then the tick")
    void deadMachineStaysDark() {
        boolean on = false;
        boolean works = false;
        on = TelHeadlights.toggled(on, works);
        assertFalse(on);
        on = TelHeadlights.toggled(false, true);
        assertTrue(on);
        works = false;
        on = TelHeadlights.afterTick(on, works);
        assertFalse(on);
        assertFalse(TelHeadlights.toggled(on, works));
    }

    @Test
    @DisplayName("the debug scenes are the five of the SPEC, by name, and the light switches are not scenes")
    void debugScenes() {
        Set<String> names = new HashSet<>(Arrays.asList(FxDebugKind.sceneNames()));
        assertEquals(Set.of("launch", "impact", "shahed", "wreck", "flash"), names);
        assertEquals(FxDebugKind.IMPACT, FxDebugKind.scene("impact").orElseThrow());
        assertEquals(FxDebugKind.IMPACT, FxDebugKind.scene("IMPACT").orElseThrow(), "whatever the case");
        assertTrue(FxDebugKind.scene("lights_on").isEmpty());
        assertTrue(FxDebugKind.scene("nonsense").isEmpty());
        assertTrue(FxDebugKind.LAUNCH.isScene() && !FxDebugKind.LIGHTS_ON.isScene() && !FxDebugKind.LIGHTS_OFF.isScene());
        assertFalse(FxDebugKind.LIGHTS_BUFFER.isScene() || FxDebugKind.LIGHTS_VOXELS.isScene());
        assertTrue(FxDebugKind.scene("lights_buffer").isEmpty() && FxDebugKind.scene("lights_voxels").isEmpty());
        for (FxDebugKind kind : FxDebugKind.values()) {
            assertEquals(kind, FxDebugKind.byOrdinal(kind.ordinal()), "the number on the wire gives the kind back");
        }
        assertEquals(FxDebugKind.FLASH, FxDebugKind.byOrdinal(99), "a number out of range is the harmless one");
    }
}
