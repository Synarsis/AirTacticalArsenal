package com.synarsis.airtacticalarsenal.event;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.network.OrlanCameraViewPacket;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrlanCameraExplosionHelper {

    private static final double MAX_VIEW_RANGE_SQ =
            Math.pow(OrlanCameraViewPacket.PATROLLING_CAMERA_CHUNK_RADIUS * 16.0, 2);
    private static final double VANILLA_FORCE_RANGE_SQ = 512.0 * 512.0;

    public static <T extends ParticleOptions> void sendParticleToFarCameraPlayers(
            ServerLevel level, T particle, double x, double y, double z,
            int count, double xOffset, double yOffset, double zOffset, double speed) {

        List<ServerPlayer> farPlayers = getFarCameraPlayersNear(level, x, y, z);
        if (farPlayers.isEmpty()) return;

        for (ServerPlayer player : farPlayers) {
            player.connection.send(new ClientboundLevelParticlesPacket(
                    particle, true, x, y, z,
                    (float) xOffset, (float) yOffset, (float) zOffset,
                    (float) speed, count));
        }
    }

    public static void sendExplosionChunkUpdates(ServerLevel level, Vec3 explosionPos) {
        List<ServerPlayer> cameraPlayers = getCameraPlayersNear(level, explosionPos.x, explosionPos.y, explosionPos.z);
        if (cameraPlayers.isEmpty()) return;

        int explosionChunkRadius = 2;
        ChunkPos center = new ChunkPos((int) explosionPos.x >> 4, (int) explosionPos.z >> 4);

        for (ServerPlayer player : cameraPlayers) {
            for (int dx = -explosionChunkRadius; dx <= explosionChunkRadius; dx++) {
                for (int dz = -explosionChunkRadius; dz <= explosionChunkRadius; dz++) {
                    ChunkAccess ca = level.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                    if (ca instanceof LevelChunk lc) {
                        player.connection.send(new ClientboundLevelChunkWithLightPacket(
                                lc, level.getLightEngine(), null, null));
                    }
                }
            }
        }
    }

    private static List<ServerPlayer> getFarCameraPlayersNear(ServerLevel level, double x, double y, double z) {
        List<ServerPlayer> result = new ArrayList<>();
        for (OrlanEntity orlan : OrlanEntity.getActiveOrlansWithCamera()) {
            if (orlan.level() != level) continue;
            if (orlan.position().distanceToSqr(x, y, z) > MAX_VIEW_RANGE_SQ) continue;

            ServerPlayer player = resolvePlayer(level, orlan.getLinkedPlayerUUID());
            if (player == null) continue;

            if (player.position().distanceToSqr(x, y, z) > VANILLA_FORCE_RANGE_SQ) {
                result.add(player);
            }
        }
        return result;
    }

    private static List<ServerPlayer> getCameraPlayersNear(ServerLevel level, double x, double y, double z) {
        List<ServerPlayer> result = new ArrayList<>();
        for (OrlanEntity orlan : OrlanEntity.getActiveOrlansWithCamera()) {
            if (orlan.level() != level) continue;
            if (orlan.position().distanceToSqr(x, y, z) > MAX_VIEW_RANGE_SQ) continue;

            ServerPlayer player = resolvePlayer(level, orlan.getLinkedPlayerUUID());
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    private static ServerPlayer resolvePlayer(ServerLevel level, String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
