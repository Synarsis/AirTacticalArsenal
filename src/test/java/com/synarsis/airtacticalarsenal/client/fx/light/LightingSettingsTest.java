package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.config.FxLighting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightingSettingsTest {
    @Test
    void qualityAndIndependentSwitchesGateEveryPass() {
        for (FxLighting quality : FxLighting.values()) {
            assertEquals(quality == FxLighting.HIGH, quality.headlightBeams(true));
            assertEquals(quality == FxLighting.MEDIUM || quality == FxLighting.HIGH, quality.bloom(true));
            assertEquals(quality != FxLighting.OFF, quality.glare(true));
            assertFalse(quality.headlightBeams(false));
            assertFalse(quality.bloom(false));
            assertFalse(quality.glare(false));
        }
    }
}
