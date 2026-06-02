package com.synarsis.airtacticalarsenal.block.entity;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AirTacticalArsenal.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IskanderRocketBlockEntity>> ISKANDER_ROCKET =
            BLOCK_ENTITIES.register("iskander_rocket", () ->
                    BlockEntityType.Builder.of(IskanderRocketBlockEntity::new, ModBlocks.ISKANDER_ROCKET.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UnifiedTerminalBlockEntity>> UNIFIED_TERMINAL =
            BLOCK_ENTITIES.register("unified_terminal", () ->
                    BlockEntityType.Builder.of(UnifiedTerminalBlockEntity::new, ModBlocks.UNIFIED_TERMINAL.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LoadingHatchBlockEntity>> LOADING_HATCH =
            BLOCK_ENTITIES.register("loading_hatch", () ->
                    BlockEntityType.Builder.of(LoadingHatchBlockEntity::new, ModBlocks.LOADING_HATCH.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherBlockEntity>> LAUNCHER =
            BLOCK_ENTITIES.register("launcher", () ->
                    BlockEntityType.Builder.of(LauncherBlockEntity::new, ModBlocks.LAUNCHER.get())
                            .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
