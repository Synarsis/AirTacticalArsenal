package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.resources.ResourceLocation;
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
    public static final DeferredBlock<Block> SHAHED_TERMINAL = registerBlock("shahed_terminal",
            () -> new ShahedTerminalBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    static {
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "unified_terminal"),
                ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "shahed_terminal"));
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
