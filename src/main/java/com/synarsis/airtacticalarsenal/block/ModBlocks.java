package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ShahedMod.MOD_ID);
    public static final RegistryObject<Block> SHAHED_RADAR = registerBlock("shahed_radar",
            () -> new ShahedRadarBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SIREN = registerBlock("siren",
            () -> new SirenBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> UNIFIED_TERMINAL = registerBlock("unified_terminal",
            () -> new UnifiedTerminalBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> LAUNCHER = registerBlock("launcher",
            () -> new LauncherBlock(BlockBehaviour.Properties.of().strength(4.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> ISKANDER_ROCKET = registerBlockNoItem("iskander_rocket",
            () -> new IskanderRocketBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion().noLootTable()));

    public static final RegistryObject<Block> LOADING_HATCH = registerBlock("loading_hatch",
            () -> new LoadingHatchBlock(BlockBehaviour.Properties.of().strength(4.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Block> RegistryObject<T> registerBlockNoItem(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
