package com.synarsis.airtacticalarsenal.worldmap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldMapData extends SavedData {
    private static final String DATA_NAME = "ata_world_map";

    private final Set<Long> coastlinePoints = ConcurrentHashMap.newKeySet();
    private final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean scanComplete = false;
    private volatile int totalChunksToScan = 0;
    private volatile int chunksScanned = 0;

    public WorldMapData() {
    }

    public static WorldMapData load(CompoundTag tag) {
        WorldMapData data = new WorldMapData();

        if (tag.contains("coastline")) {
            ListTag coastlineTag = tag.getList("coastline", Tag.TAG_LONG);
            for (int i = 0; i < coastlineTag.size(); i++) {
                data.coastlinePoints.add(((net.minecraft.nbt.LongTag) coastlineTag.get(i)).getAsLong());
            }
        }

        if (tag.contains("scannedChunks")) {
            ListTag scannedTag = tag.getList("scannedChunks", Tag.TAG_LONG);
            for (int i = 0; i < scannedTag.size(); i++) {
                data.scannedChunks.add(((net.minecraft.nbt.LongTag) scannedTag.get(i)).getAsLong());
            }
        }

        data.scanComplete = tag.getBoolean("scanComplete");
        data.chunksScanned = data.scannedChunks.size();

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag coastlineTag = new ListTag();
        for (Long point : coastlinePoints) {
            coastlineTag.add(net.minecraft.nbt.LongTag.valueOf(point));
        }
        tag.put("coastline", coastlineTag);

        ListTag scannedTag = new ListTag();
        for (Long chunk : scannedChunks) {
            scannedTag.add(net.minecraft.nbt.LongTag.valueOf(chunk));
        }
        tag.put("scannedChunks", scannedTag);

        tag.putBoolean("scanComplete", scanComplete);

        return tag;
    }

    public static WorldMapData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(WorldMapData::load, WorldMapData::new, DATA_NAME);
    }

    public void addCoastlinePoint(int x, int z) {
        coastlinePoints.add(packPos(x, z));
        setDirty();
    }

    public void markChunkScanned(int chunkX, int chunkZ) {
        scannedChunks.add(packChunkPos(chunkX, chunkZ));
        chunksScanned = scannedChunks.size();
        setDirty();
    }

    public boolean isChunkScanned(int chunkX, int chunkZ) {
        return scannedChunks.contains(packChunkPos(chunkX, chunkZ));
    }

    public Set<Long> getCoastlinePoints() {
        return coastlinePoints;
    }

    public Set<Long> getCoastlinePointsInRadius(int centerX, int centerZ, int radius) {
        Set<Long> result = new HashSet<>();
        int radiusSq = radius * radius;

        for (Long packed : coastlinePoints) {
            int x = unpackX(packed);
            int z = unpackZ(packed);
            int dx = x - centerX;
            int dz = z - centerZ;
            if (dx * dx + dz * dz <= radiusSq) {
                result.add(packed);
            }
        }

        return result;
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public void setScanComplete(boolean complete) {
        this.scanComplete = complete;
        setDirty();
    }

    public int getTotalChunksToScan() {
        return totalChunksToScan;
    }

    public void setTotalChunksToScan(int total) {
        this.totalChunksToScan = total;
    }

    public int getChunksScanned() {
        return chunksScanned;
    }

    public int getCoastlinePointCount() {
        return coastlinePoints.size();
    }

    public void clear() {
        coastlinePoints.clear();
        scannedChunks.clear();
        scanComplete = false;
        totalChunksToScan = 0;
        chunksScanned = 0;
        setDirty();
    }

    public static long packPos(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int unpackX(long packed) {
        return (int) packed;
    }

    public static int unpackZ(long packed) {
        return (int) (packed >> 32);
    }
}
