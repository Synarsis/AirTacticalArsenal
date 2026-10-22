package com.synarsis.airtacticalarsenal.entity.patriot;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class TractorInput {

    public static IntSupplier provider = () -> 0;

    public static Consumer<M983TractorEntity> clientTick = entity -> {
    };

    public static Consumer<M983TractorEntity> openPanel = entity -> {
    };

    private TractorInput() {
    }
}
