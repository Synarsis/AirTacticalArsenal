package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AirTacticalArsenal.MODID);

    public static final DeferredBlock<Block> SHAHED_RADAR = registerBlock("shahed_radar",
            () -> new ShahedRadarBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SIREN = registerBlock("siren",
            () -> new SirenBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> UNIFIED_TERMINAL = registerBlock("unified_terminal",
            () -> new UnifiedTerminalBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SHAHED_TERMINAL = registerBlock("shahed_terminal",
            () -> new ShahedTerminalBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> LAUNCHER = registerBlock("launcher",
            () -> new LauncherBlock(BlockBehaviour.Properties.of().strength(4.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredBlock<Block> ISKANDER_ROCKET = registerBlockNoItem("iskander_rocket",
            () -> new IskanderRocketBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion().noLootTable()));

    public static final DeferredBlock<Block> LOADING_HATCH = registerBlock("loading_hatch",
            () -> new LoadingHatchBlock(BlockBehaviour.Properties.of().strength(4.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Block> DeferredBlock<T> registerBlockNoItem(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
