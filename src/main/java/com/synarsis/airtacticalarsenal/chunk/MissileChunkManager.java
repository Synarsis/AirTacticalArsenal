package com.synarsis.airtacticalarsenal.chunk;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;

public class MissileChunkManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissileChunkManager.class);
    private static final ResourceLocation TICKET_ID =
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "missile_chunk");

    private static final TicketController TICKETS = new TicketController(TICKET_ID);

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    private static final int CHUNK_LOAD_RADIUS = 1;

    private static final int PRELOAD_CHUNKS_AHEAD = 3;

    private static final Map<Integer, Set<ChunkPos>> loadedChunksPerMissile = new ConcurrentHashMap<>();

    private static final Map<Integer, ServerLevel> missileLevels = new ConcurrentHashMap<>();

    private static final Map<Integer, java.util.UUID> missileOwners = new ConcurrentHashMap<>();

    private static final Map<Integer, ChunkPos> lastChunkPosCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> lastRadiusCache = new ConcurrentHashMap<>();

    public static void registerMissile(Entity missile, ServerLevel level) {
        int entityId = missile.getId();
        loadedChunksPerMissile.put(entityId, ConcurrentHashMap.newKeySet());
        missileLevels.put(entityId, level);
        missileOwners.put(entityId, missile.getUUID());

        updateChunksForMissile(missile, level);
    }

    public static void updateChunksForMissile(Entity missile, ServerLevel level) {
        updateChunksWithRadius(missile, level, CHUNK_LOAD_RADIUS);
    }

    public static void updateChunksWithRadius(Entity missile, ServerLevel level, int loadRadius) {
        if (missile == null || level == null || missile.isRemoved()) {
            return;
        }

        int entityId = missile.getId();
        Set<ChunkPos> currentLoaded = loadedChunksPerMissile.get(entityId);
        if (currentLoaded == null) {
            registerMissile(missile, level);
            currentLoaded = loadedChunksPerMissile.get(entityId);
            if (currentLoaded == null) return;
        }

        BlockPos currentPos = missile.blockPosition();
        ChunkPos currentChunk = new ChunkPos(currentPos);

        ChunkPos lastChunk = lastChunkPosCache.get(entityId);
        Integer lastRadius = lastRadiusCache.get(entityId);
        if (lastChunk != null && lastChunk.equals(currentChunk)
                && lastRadius != null && lastRadius == loadRadius) {
            return;
        }
        lastChunkPosCache.put(entityId, currentChunk);
        lastRadiusCache.put(entityId, loadRadius);

        Set<ChunkPos> chunksToLoad = new HashSet<>();

        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dz = -loadRadius; dz <= loadRadius; dz++) {
                chunksToLoad.add(new ChunkPos(currentChunk.x + dx, currentChunk.z + dz));
            }
        }

        var movement = missile.getDeltaMovement();
        if (movement.lengthSqr() > 0.01) {
            double dirX = movement.x;
            double dirZ = movement.z;
            double length = Math.sqrt(dirX * dirX + dirZ * dirZ);

            if (length > 0.01) {
                dirX /= length;
                dirZ /= length;

                for (int i = 1; i <= PRELOAD_CHUNKS_AHEAD; i++) {
                    int aheadX = currentChunk.x + (int) Math.round(dirX * i);
                    int aheadZ = currentChunk.z + (int) Math.round(dirZ * i);
                    chunksToLoad.add(new ChunkPos(aheadX, aheadZ));
                }
            }
        }

        Set<ChunkPos> chunksToUnload = new HashSet<>(currentLoaded);
        chunksToUnload.removeAll(chunksToLoad);

        for (ChunkPos chunk : chunksToUnload) {
            TICKETS.forceChunk(level, missile, chunk.x, chunk.z, false, true);
            currentLoaded.remove(chunk);
        }

        Set<ChunkPos> chunksToLoadNew = new HashSet<>(chunksToLoad);
        chunksToLoadNew.removeAll(currentLoaded);

        for (ChunkPos chunk : chunksToLoadNew) {
            TICKETS.forceChunk(level, missile, chunk.x, chunk.z, true, true);
            currentLoaded.add(chunk);
        }
    }

    public static void unregisterMissile(Entity missile) {
        if (missile == null) return;

        int entityId = missile.getId();
        Set<ChunkPos> loadedChunks = loadedChunksPerMissile.remove(entityId);
        ServerLevel level = missileLevels.remove(entityId);
        java.util.UUID owner = missileOwners.remove(entityId);
        lastChunkPosCache.remove(entityId);
        lastRadiusCache.remove(entityId);

        if (level == null || owner == null || loadedChunks == null) return;
        for (ChunkPos chunk : loadedChunks) {
            try {
                TICKETS.forceChunk(level, owner, chunk.x, chunk.z, false, true);
            } catch (Exception ignored) {}
        }
    }

    public static void cleanup() {
        for (Map.Entry<Integer, Set<ChunkPos>> entry : loadedChunksPerMissile.entrySet()) {
            ServerLevel level = missileLevels.get(entry.getKey());
            java.util.UUID owner = missileOwners.get(entry.getKey());
            if (level != null && owner != null) {
                for (ChunkPos chunk : entry.getValue()) {
                    try {
                        TICKETS.forceChunk(level, owner, chunk.x, chunk.z, false, true);
                    } catch (Exception ignored) {}
                }
            }
        }
        loadedChunksPerMissile.clear();
        missileOwners.clear();
        missileLevels.clear();
        lastChunkPosCache.clear();
        lastRadiusCache.clear();
    }

    public static void preloadTargetArea(Entity missile, ServerLevel level, BlockPos targetPos) {
        if (missile == null || level == null || missile.isRemoved()) return;

        int entityId = missile.getId();
        Set<ChunkPos> currentLoaded = loadedChunksPerMissile.get(entityId);
        if (currentLoaded == null) {
            registerMissile(missile, level);
            currentLoaded = loadedChunksPerMissile.get(entityId);
            if (currentLoaded == null) return;
        }

        ChunkPos center = new ChunkPos(targetPos);
        int radius = 2;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (!currentLoaded.contains(chunk)) {
                    TICKETS.forceChunk(level, missile, chunk.x, chunk.z, true, true);
                    currentLoaded.add(chunk);
                }
            }
        }
    }

    public static boolean isMissileRegistered(Entity missile) {
        return loadedChunksPerMissile.containsKey(missile.getId());
    }

    public static int getLoadedChunkCount(Entity missile) {
        Set<ChunkPos> chunks = loadedChunksPerMissile.get(missile.getId());
        return chunks != null ? chunks.size() : 0;
    }
}
