package com.synarsis.airtacticalarsenal;

import com.synarsis.airtacticalarsenal.block.ModBlocks;
import com.synarsis.airtacticalarsenal.block.entity.ModBlockEntities;
import com.synarsis.airtacticalarsenal.chunk.MissileChunkManager;
import com.synarsis.airtacticalarsenal.command.ShahedCommand;
import com.synarsis.airtacticalarsenal.compat.CreateBlockingHandler;
import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.item.ModCreativeTabs;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import com.synarsis.airtacticalarsenal.recipe.ModRecipes;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import com.synarsis.airtacticalarsenal.worldmap.WorldMapScanner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

@Mod("ata")
public class ShahedMod {
    public static final String MOD_ID = "ata";

    public ShahedMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModRecipes.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShahedConfig.SPEC, "ata-common.toml");
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CreateBlockingHandler());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();

            registerCreateBlockProtection();

            ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, ticketHelper) -> {

                ticketHelper.getEntityTickets().forEach((uuid, chunks) -> {
                    chunks.getFirst().forEach(chunk -> ticketHelper.removeTicket(uuid, chunk, true));
                    chunks.getSecond().forEach(chunk -> ticketHelper.removeTicket(uuid, chunk, false));
                });
            });
        });
    }

    private void registerCreateBlockProtection() {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        List<Block> ataBlocks = new ArrayList<>();
        ModBlocks.BLOCKS.getEntries().forEach(ro -> ataBlocks.add(ro.get()));
        CreateCompat.registerBlockProtection(ataBlocks);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ShahedCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            WorldMapScanner.getInstance().startScan(overworld);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {

        MissileChunkManager.cleanup();
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!(result.getItem() instanceof SprayCanItem)) return;

        CompoundTag tag = result.getTag();
        if (tag == null || !tag.contains("ExtraDyeConsume")) return;

        int extraConsume = tag.getInt("ExtraDyeConsume");
        tag.remove("ExtraDyeConsume");

        Container container = event.getInventory();
        for (int i = 0; i < container.getContainerSize() && extraConsume > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(Items.BLACK_DYE)) {
                int toConsume = Math.min(stack.getCount(), extraConsume);
                stack.shrink(toConsume);
                extraConsume -= toConsume;
            }
        }
    }
}
