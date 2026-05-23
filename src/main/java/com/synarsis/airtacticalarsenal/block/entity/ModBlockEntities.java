package com.synarsis.airtacticalarsenal.block.entity;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ShahedMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<IskanderRocketBlockEntity>> ISKANDER_ROCKET = 
        BLOCK_ENTITIES.register("iskander_rocket", () -> 
            BlockEntityType.Builder.of(IskanderRocketBlockEntity::new, ModBlocks.ISKANDER_ROCKET.get())
                .build(null));

    public static final RegistryObject<BlockEntityType<UnifiedTerminalBlockEntity>> UNIFIED_TERMINAL = 
        BLOCK_ENTITIES.register("unified_terminal", () -> 
            BlockEntityType.Builder.of(UnifiedTerminalBlockEntity::new, ModBlocks.UNIFIED_TERMINAL.get())
                .build(null));

    public static final RegistryObject<BlockEntityType<LoadingHatchBlockEntity>> LOADING_HATCH = 
        BLOCK_ENTITIES.register("loading_hatch", () -> 
            BlockEntityType.Builder.of(LoadingHatchBlockEntity::new, ModBlocks.LOADING_HATCH.get())
                .build(null));

    public static final RegistryObject<BlockEntityType<LauncherBlockEntity>> LAUNCHER = 
        BLOCK_ENTITIES.register("launcher", () -> 
            BlockEntityType.Builder.of(LauncherBlockEntity::new, ModBlocks.LAUNCHER.get())
                .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
