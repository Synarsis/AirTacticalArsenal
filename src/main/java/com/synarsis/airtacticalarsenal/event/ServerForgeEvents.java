package com.synarsis.airtacticalarsenal.event;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.command.ShahedCommand;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.network.IskanderSpawnPacket;
import com.synarsis.airtacticalarsenal.radar.ServerRouteMapScanner;
import com.synarsis.airtacticalarsenal.worldmap.ServerTerrainScanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Game event bus subscriber. Handles entity tracking (re-syncing Iskander state on
 * tracking start) and per-tick maintenance of the radar scanners.
 *
 * Subscribed to the game/NeoForge event bus via @EventBusSubscriber(modid=...).
 */
@EventBusSubscriber(modid = AirTacticalArsenal.MODID)
public class ServerForgeEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ShahedCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof IskanderEntity iskander
                && event.getEntity() instanceof ServerPlayer serverPlayer
                && iskander.isLegitSpawn) {
            PacketDistributor.sendToPlayer(serverPlayer, new IskanderSpawnPacket(
                    iskander.getId(),
                    iskander.position(),
                    iskander.getTargetPos(),
                    iskander.getFlightPhase(),
                    iskander.getFlightTicks(),
                    null,
                    iskander.getStartPos(),
                    iskander.getLaunchEndPos(),
                    iskander.getFinalTargetPos()
            ));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        // Interleave ServerTerrainScanner and ServerRouteMapScanner ticking by the lower
        // buildStartOrder so map-builds happen first-come-first-served (mirrors Forge).
        long terrainOrder = ServerTerrainScanner.getFirstBuildingOrder();
        long routeOrder = ServerRouteMapScanner.getFirstBuildingOrder();
        if (terrainOrder <= routeOrder) {
            if (!ServerTerrainScanner.tickAll(overworld)) {
                ServerRouteMapScanner.tickAll(overworld);
            }
        } else {
            if (!ServerRouteMapScanner.tickAll(overworld)) {
                ServerTerrainScanner.tickAll(overworld);
            }
        }
    }
}
