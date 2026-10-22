package com.synarsis.airtacticalarsenal.client.fx.light;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

public record CameraPrecipitation(double roadRain, double beamRain, double thunder, boolean snow) {

    public static CameraPrecipitation of(Biome.Precipitation precipitation, double rain, double thunder) {
        return switch (precipitation) {
            case RAIN -> new CameraPrecipitation(rain, rain, thunder, false);
            case SNOW -> new CameraPrecipitation(0.0, rain, thunder, true);
            default -> new CameraPrecipitation(0.0, 0.0, 0.0, false);
        };
    }

    public static CameraPrecipitation at(ClientLevel level, Vec3 camera, float partial) {
        BlockPos position = BlockPos.containing(camera);
        Biome.Precipitation precipitation = level.getBiome(position).value().getPrecipitationAt(position);
        return of(precipitation, level.getRainLevel(partial), level.getThunderLevel(partial));
    }
}
