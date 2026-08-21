package com.synarsis.airtacticalarsenal;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.block.ModBlocks;
import com.synarsis.airtacticalarsenal.chunk.MissileChunkManager;
import com.synarsis.airtacticalarsenal.block.entity.ModBlockEntities;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.item.ModCreativeTabs;
import com.synarsis.airtacticalarsenal.item.ModDataComponents;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import com.synarsis.airtacticalarsenal.recipe.ModRecipes;
import com.synarsis.airtacticalarsenal.sound.ModSounds;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(AirTacticalArsenal.MODID)
public class AirTacticalArsenal {
    public static final String MODID = "ata";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AirTacticalArsenal(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModEntities.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModRecipes.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(NetworkHandler::register);
        // Chunk tickets owned per missile, instead of the level-wide forced-chunk set.
        modEventBus.addListener(MissileChunkManager::registerTicketController);
        NeoForge.EVENT_BUS.register(this);

        // SERVER, not COMMON. NeoForge only synchronises SERVER-type configs to clients, and every
        // value here drives shared behaviour: launch ranges, damage multipliers, protected zones.
        // As a COMMON config the client read its own local file while the server applied a
        // different one, so the terminal screen could enable a launch the server then refused.
        modContainer.registerConfig(ModConfig.Type.SERVER, ShahedConfig.SPEC, "ata-server.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Air Tactical Arsenal common setup");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
