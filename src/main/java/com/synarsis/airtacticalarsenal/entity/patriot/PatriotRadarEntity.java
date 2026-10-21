package com.synarsis.airtacticalarsenal.entity.patriot;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class PatriotRadarEntity extends PatriotVehicleEntity {

    public PatriotRadarEntity(EntityType<? extends PatriotRadarEntity> type, Level level) {
        super(type, level, PatriotKind.RADAR);
    }

    @Override
    public PatriotKind kind() {
        return PatriotKind.RADAR;
    }
}
