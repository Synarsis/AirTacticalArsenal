package com.synarsis.airtacticalarsenal.worldmap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldMapScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-WorldMap");

    private static WorldMapScanner instance;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATA-MapScanner");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private volatile int totalChunks = 0;

    private WorldMapScanner() {}

    public static WorldMapScanner getInstance() {
        if (instance == null) {
            instance = new WorldMapScanner();
        }
        return instance;
    }

    public void startScan(ServerLevel level) {
        if (isScanning.get()) {
            LOGGER.info("[ATA] Map scan already in progress");
            return;
        }

        WorldMapData mapData = WorldMapData.get(level);

        if (mapData.isScanComplete()) {
            LOGGER.info("[ATA] Map already scanned ({} coastline points)", mapData.getCoastlinePointCount());
            return;
        }

        isScanning.set(true);

        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[ATA] Starting world map scan...");

                List<ChunkPos> chunksToScan = findExistingChunks(level);
                totalChunks = chunksToScan.size();
                mapData.setTotalChunksToScan(totalChunks);

                LOGGER.info("[ATA] Found {} chunks to scan", totalChunks);

                int scannedCount = 0;
                int batchSize = 100;

                for (int i = 0; i < chunksToScan.size(); i += batchSize) {
                    if (!isScanning.get()) {
                        LOGGER.info("[ATA] Scan cancelled");
                        return;
                    }

                    int end = Math.min(i + batchSize, chunksToScan.size());
                    List<ChunkPos> batch = chunksToScan.subList(i, end);

                    level.getServer().execute(() -> {
                        for (ChunkPos pos : batch) {
                            if (mapData.isChunkScanned(pos.x, pos.z)) {
                                continue;
                            }
                            scanChunk(level, pos, mapData);
                        }
                    });

                    scannedCount += batch.size();
                    progress.set(scannedCount);

                    if (scannedCount % 1000 == 0) {
                        LOGGER.info("[ATA] Scan progress: {}/{} chunks ({}%)", 
                            scannedCount, totalChunks, (scannedCount * 100 / totalChunks));
                    }

                    Thread.sleep(50);
                }

                mapData.setScanComplete(true);
                LOGGER.info("[ATA] Map scan complete! {} coastline points found", mapData.getCoastlinePointCount());

            } catch (Exception e) {
                LOGGER.error("[ATA] Error during map scan", e);
            } finally {
                isScanning.set(false);
            }
        }, scanExecutor);
    }

    private List<ChunkPos> findExistingChunks(ServerLevel level) {
        List<ChunkPos> chunks = new ArrayList<>();

        Path worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        File regionDir = worldPath.resolve("region").toFile();

        if (!regionDir.exists() || !regionDir.isDirectory()) {
            LOGGER.warn("[ATA] Region directory not found: {}", regionDir);
            return chunks;
        }

        File[] regionFiles = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));
        if (regionFiles == null) {
            return chunks;
        }

        for (File regionFile : regionFiles) {
            String name = regionFile.getName();
            String[] parts = name.replace("r.", "").replace(".mca", "").split("\\.");
            if (parts.length != 2) continue;

            try {
                int regionX = Integer.parseInt(parts[0]);
                int regionZ = Integer.parseInt(parts[1]);

                List<ChunkPos> regionChunks = getChunksInRegion(regionFile, regionX, regionZ);
                chunks.addAll(regionChunks);

            } catch (NumberFormatException e) {
                LOGGER.warn("[ATA] Invalid region file name: {}", name);
            }
        }

        return chunks;
    }

    private List<ChunkPos> getChunksInRegion(File regionFile, int regionX, int regionZ) {
        List<ChunkPos> chunks = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    int headerOffset = 4 * (localX + localZ * 32);
                    raf.seek(headerOffset);

                    int offset = raf.readInt();
                    if (offset != 0) {
                        int chunkX = regionX * 32 + localX;
                        int chunkZ = regionZ * 32 + localZ;
                        chunks.add(new ChunkPos(chunkX, chunkZ));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ATA] Error reading region file: {}", regionFile.getName());
        }

        return chunks;
    }

    private static final int SCAN_STEP = 4;

    private void scanChunk(ServerLevel level, ChunkPos pos, WorldMapData mapData) {
        try {
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk == null) {
                return;
            }

            for (int localX = 0; localX < 16; localX += SCAN_STEP) {
                for (int localZ = 0; localZ < 16; localZ += SCAN_STEP) {
                    int worldX = pos.getMinBlockX() + localX;
                    int worldZ = pos.getMinBlockZ() + localZ;

                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                    BlockPos blockPos = new BlockPos(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(blockPos);

                    boolean isWater = state.is(Blocks.WATER) || state.is(Blocks.ICE) || 
                                      state.is(Blocks.FROSTED_ICE) || state.is(Blocks.BLUE_ICE) ||
                                      state.is(Blocks.PACKED_ICE);

                    if (isCoastlineOptimized(chunk, localX, localZ, isWater)) {
                        mapData.addCoastlinePoint(worldX, worldZ);
                    }
                }
            }

            mapData.markChunkScanned(pos.x, pos.z);

        } catch (Exception e) {
        }
    }

    private boolean isCoastlineOptimized(ChunkAccess chunk, int localX, int localZ, boolean isWater) {
        int[][] neighbors = {{-SCAN_STEP, 0}, {SCAN_STEP, 0}, {0, -SCAN_STEP}, {0, SCAN_STEP}};

        for (int[] offset : neighbors) {
            int nx = localX + offset[0];
            int nz = localZ + offset[1];

            if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) {
                continue;
            }

            int ny = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, nx, nz);
            BlockPos neighborPos = new BlockPos(
                chunk.getPos().getMinBlockX() + nx, 
                ny, 
                chunk.getPos().getMinBlockZ() + nz
            );
            BlockState neighborState = chunk.getBlockState(neighborPos);

            boolean neighborIsWater = neighborState.is(Blocks.WATER) || neighborState.is(Blocks.ICE) ||
                                      neighborState.is(Blocks.FROSTED_ICE) || neighborState.is(Blocks.BLUE_ICE) ||
                                      neighborState.is(Blocks.PACKED_ICE);

            if (isWater != neighborIsWater) {
                return true;
            }
        }

        return false;
    }

    public void stopScan() {
        isScanning.set(false);
    }

    public boolean isScanning() {
        return isScanning.get();
    }

    public int getProgress() {
        return progress.get();
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public float getProgressPercent() {
        if (totalChunks == 0) return 0;
        return (float) progress.get() / totalChunks * 100f;
    }
}
