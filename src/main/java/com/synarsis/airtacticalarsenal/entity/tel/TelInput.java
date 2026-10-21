package com.synarsis.airtacticalarsenal.entity.tel;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

public final class TelInput {

    public static final int FWD = 1;
    public static final int BACK = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int BRAKE = 16;
    public static final int BOOST = 32;

    public static IntSupplier provider = () -> 0;

    public static Consumer<IskanderTelEntity> clientTick = entity -> {
    };

    public static java.util.function.BiConsumer<IskanderTelEntity, Boolean> destroyedFx = (entity, detonated) -> {
    };

    public static DoubleSupplier partialTick = () -> 1.0;

    public static volatile boolean traceEnabled;

    public static Consumer<String> traceSink = line -> {
    };

    private TelInput() {
    }
}
