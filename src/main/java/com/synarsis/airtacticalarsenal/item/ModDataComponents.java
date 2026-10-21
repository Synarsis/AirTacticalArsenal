package com.synarsis.airtacticalarsenal.item;

import com.mojang.serialization.Codec;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AirTacticalArsenal.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> SPRAY_CHARGE =
            register("spray_charge", b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HAS_WATER =
            register("has_water", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> REGISTERED_LAUNCHER =
            register("registered_launcher",
                    b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> REGISTERED_LAUNCHER_DIMENSION =
            register("registered_launcher_dimension",
                    b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private static final StreamCodec<ByteBuf, List<BlockPos>> BLOCK_POS_LIST_STREAM_CODEC =
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BlockPos>>> LINKED_LAUNCHERS =
            register("linked_launchers",
                    b -> b.persistent(BlockPos.CODEC.listOf())
                            .networkSynchronized(BLOCK_POS_LIST_STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SavedCoords>> SAVED_COORDS =
            register("saved_coords",
                    b -> b.persistent(SavedCoords.CODEC).networkSynchronized(SavedCoords.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> LINKED_ENTITY =
            register("linked_entity", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LINKED_ORLAN_ID =
            register("linked_orlan_id", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CAMERA_MODE =
            register("camera_mode", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SHAHED_COLOR =
            register("shahed_color", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> op) {
        return COMPONENTS.registerComponentType(name, op);
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
