package com.synarsis.airtacticalarsenal.entity.patriot;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PatriotInput {

    public static Consumer<PatriotVehicleEntity> openPanel = entity -> {
    };

    public static BiConsumer<PatriotVehicleEntity, Boolean> destroyedFx = (entity, detonated) -> {
    };

    private PatriotInput() {
    }
}
