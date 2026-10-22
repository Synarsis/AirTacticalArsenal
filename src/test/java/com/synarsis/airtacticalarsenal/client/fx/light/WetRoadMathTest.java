package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WetRoadMathTest {
    @Test
    void roofDryWeatherAndVerticalWallAreDry() {
        assertEquals(0, WetRoadMath.specular(1, 1, 0.5, 1, 10, false));
        assertEquals(0, WetRoadMath.specular(1, 1, 0.5, 0, 10, true));
        assertEquals(0, WetRoadMath.specular(0.7, 1, 0.5, 1, 10, true));
        for (int index = 0; index < 6; index++) {
            double[] cells = new double[6];
            cells[index] = 1;
            assertFalse(WetRoadMath.open(cells));
        }
        assertTrue(WetRoadMath.open(new double[6]));
    }
    @Test
    void precipitationAtTheCameraDecidesRoadAndBeams() {
        CameraPrecipitation rain = CameraPrecipitation.of(net.minecraft.world.level.biome.Biome.Precipitation.RAIN, 0.8, 0.5);
        assertEquals(0.8, rain.roadRain(), 0.0);
        assertEquals(0.8, rain.beamRain(), 0.0);
        assertEquals(0.5, rain.thunder(), 0.0);
        assertFalse(rain.snow());
        CameraPrecipitation snow = CameraPrecipitation.of(net.minecraft.world.level.biome.Biome.Precipitation.SNOW, 0.8, 0.5);
        assertEquals(0.0, snow.roadRain(), 0.0);
        assertEquals(0.8, snow.beamRain(), 0.0);
        assertTrue(snow.snow());
        assertEquals(0.0, WetRoadMath.specular(1, 1, 0.5, snow.roadRain(), 10, true), 0.0);
        CameraPrecipitation none = CameraPrecipitation.of(net.minecraft.world.level.biome.Biome.Precipitation.NONE, 0.8, 0.5);
        assertEquals(0.0, none.roadRain(), 0.0);
        assertEquals(0.0, none.beamRain(), 0.0);
        assertEquals(0.0, none.thunder(), 0.0);
        assertFalse(none.snow());
        assertEquals(0.0, WetRoadMath.specular(1, 1, 0.5, none.roadRain(), 10, true), 0.0);
        assertTrue(BeamMath.density(none.beamRain(), none.thunder(), 64.0, none.snow())
                < BeamMath.density(rain.beamRain(), rain.thunder(), 64.0, rain.snow()));
        assertEquals(BeamMath.density(0.0, 0.0, 64.0, false),
                BeamMath.density(none.beamRain(), none.thunder(), 64.0, none.snow()), 0.0);
        assertTrue(BeamMath.density(snow.beamRain(), snow.thunder(), 64.0, snow.snow())
                > BeamMath.density(0.8, 0.5, 64.0, false));
    }

    @Test
    void fresnelExponentAndLinearity() {
        assertEquals(0.012, WetRoadMath.specular(1, 1, 1, 1, 1, true), 1e-12);
        assertEquals(0.6, WetRoadMath.specular(1, 1, 0, 1, 1, true), 1e-12);
        double highlight = WetRoadMath.specular(1, 0.9, 0.2, 0.4, 2, true);
        assertTrue(highlight > 0);
        assertEquals(2 * highlight, WetRoadMath.specular(1, 0.9, 0.2, 0.4, 4, true), 1e-12);
    }
}
