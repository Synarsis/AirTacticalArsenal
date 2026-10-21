package com.synarsis.airtacticalarsenal.entity.patriot;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class PatriotLauncherEntity extends PatriotVehicleEntity {

    public PatriotLauncherEntity(EntityType<? extends PatriotLauncherEntity> type, Level level) {
        super(type, level, PatriotKind.LAUNCHER);
    }

    @Override
    public PatriotKind kind() {
        return PatriotKind.LAUNCHER;
    }
}
