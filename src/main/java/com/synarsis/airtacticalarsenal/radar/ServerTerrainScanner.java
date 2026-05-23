package com.synarsis.airtacticalarsenal.radar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerTerrainScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-RadarMap");
    private static final int CACHE_SIZE = 1536;
    private static final int CACHE_BPP = 2;
    private static final int CACHE_VERSION = 16;
    private static final long MAX_TICK_MS = 25;

    private static final Map<String, ServerTerrainScanner> INSTANCES = new LinkedHashMap<>();

    public static long GLOBAL_BUILD_ORDER = 0;

    private final BlockPos radarPos;
    private final int originX, originZ;
    private int buildRow = -1;
    private int buildCol = 0;
    private boolean ready = false;
    private long buildStartOrder = 0;
    private int[] pixels;
    private byte[] pngBytes;

    private final Map<Long, Integer> biomeColorCache = new HashMap<>();

    public ServerTerrainScanner(BlockPos radarPos) {
        this.radarPos = radarPos;
        this.originX = radarPos.getX() - (CACHE_SIZE / 2) * CACHE_BPP;
        this.originZ = radarPos.getZ() - (CACHE_SIZE / 2) * CACHE_BPP;
        this.pixels = new int[CACHE_SIZE * CACHE_SIZE];
    }

    public static String key(BlockPos pos) {
        return pos.getX() + "_" + pos.getZ();
    }

    public static synchronized ServerTerrainScanner getOrCreate(BlockPos pos, ServerLevel level) {
        String k = key(pos);
        ServerTerrainScanner scanner = INSTANCES.get(k);
        if (scanner != null) return scanner;

        scanner = new ServerTerrainScanner(pos);
        byte[] cached = scanner.tryLoadFromFile(level);
        if (cached != null) {
            scanner.pngBytes = cached;
            scanner.ready = true;
            scanner.pixels = null;
        } else {
            scanner.buildRow = 0;
            scanner.buildStartOrder = ++GLOBAL_BUILD_ORDER;
        }
        INSTANCES.put(k, scanner);
        return scanner;
    }

    public static synchronized void remove(BlockPos pos) {
        INSTANCES.remove(key(pos));
    }

    public static synchronized void removeAndInvalidate(BlockPos pos, ServerLevel level) {
        INSTANCES.remove(key(pos));
        if (level != null) {
            try {
                Path worldPath = level.getServer().getWorldPath(LevelResource.ROOT);
                Path file = worldPath.resolve("ata_radar_cache")
                        .resolve("radar_v" + CACHE_VERSION + "_" + pos.getX() + "_" + pos.getZ() + ".png");
                Files.deleteIfExists(file);
            } catch (Exception e) {
                LOGGER.warn("[ATA] Failed to delete radar cache for {}", key(pos));
            }
        }
    }

    public boolean isReady() { return ready; }
    public boolean isBuilding() { return buildRow >= 0; }
    public float getProgress() { return buildRow >= 0 ? (float)(buildRow * CACHE_SIZE + buildCol) / (CACHE_SIZE * CACHE_SIZE) : 1.0f; }
    public byte[] getPngBytes() { return pngBytes; }

    public static synchronized int getQueuePosition(BlockPos pos) {
        String targetKey = key(pos);
        ServerTerrainScanner target = INSTANCES.get(targetKey);
        if (target == null || !target.isBuilding()) return 0;
        int position = 1;
        for (ServerTerrainScanner s : INSTANCES.values()) {
            if (s.isBuilding() && s.buildStartOrder < target.buildStartOrder) position++;
        }
        position += ServerRouteMapScanner.countBuildingBefore(target.buildStartOrder);
        return position;
    }

    public static synchronized int countBuildingBefore(long order) {
        int c = 0;
        for (ServerTerrainScanner s : INSTANCES.values()) {
            if (s.isBuilding() && s.buildStartOrder < order) c++;
        }
        return c;
    }

    public static synchronized long getFirstBuildingOrder() {
        for (ServerTerrainScanner s : INSTANCES.values()) {
            if (s.isBuilding()) return s.buildStartOrder;
        }
        return Long.MAX_VALUE;
    }

    public static synchronized boolean tickAll(ServerLevel level) {
        for (ServerTerrainScanner scanner : INSTANCES.values()) {
            if (scanner.isBuilding()) {
                scanner.tick(level);
                return true;
            }
        }
        return false;
    }

    public void tick(ServerLevel level) {
        if (ready || buildRow < 0) return;

        long start = System.currentTimeMillis();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        BiomeSource bs = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int pixelsDone = 0;

        while (buildRow < CACHE_SIZE) {
            int wx = originX + buildCol * CACHE_BPP;
            int wz = originZ + buildRow * CACHE_BPP;
            pixels[buildRow * CACHE_SIZE + buildCol] = sampleColor(level, wx, wz, mPos, bs, sampler);
            buildCol++;
            pixelsDone++;
            if (buildCol >= CACHE_SIZE) {
                buildCol = 0;
                buildRow++;
            }
            if ((pixelsDone & 31) == 0 && System.currentTimeMillis() - start > MAX_TICK_MS) break;
        }

        if (buildRow >= CACHE_SIZE) {
            buildRow = -1;
            ready = true;
            pngBytes = convertToPng();
            saveToDisk(level);
            biomeColorCache.clear();
            pixels = null;
            LOGGER.info("[ATA] Radar map ready for {}", key(radarPos));
        }
    }

    private int sampleColor(ServerLevel level, int wx, int wz, BlockPos.MutableBlockPos mPos,
                             BiomeSource bs, Climate.Sampler sampler) {
        ChunkAccess chunk = level.getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
        if (chunk == null) {

            chunk = level.getChunk(wx >> 4, wz >> 4, ChunkStatus.BIOMES, false);
            if (chunk != null) {
                Holder<Biome> bh = chunk.getNoiseBiome(wx >> 2, 16, wz >> 2);
                return biomeOnlyColor(bh);
            }

            long ck = ((long)(wx >> 2) << 32) | ((wz >> 2) & 0xFFFFFFFFL);
            Integer cached = biomeColorCache.get(ck);
            if (cached != null) return cached;
            Holder<Biome> bh = bs.getNoiseBiome(wx >> 2, 16, wz >> 2, sampler);
            int color = biomeOnlyColor(bh);
            biomeColorCache.put(ck, color);
            return color;
        }

        int h = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx & 15, wz & 15) + 1;

        Holder<Biome> bh = chunk.getNoiseBiome(wx >> 2, Math.max(h, 64) >> 2, wz >> 2);
        Biome biome = bh.value();

        mPos.set(wx, h, wz);
        BlockState st = chunk.getBlockState(mPos);
        boolean water = st.is(Blocks.WATER) || st.is(Blocks.ICE) || st.is(Blocks.FROSTED_ICE);
        String bn = bh.unwrapKey().map(k -> k.location().getPath()).orElse("");

        int r, g, b;
        if (water || bn.contains("ocean") || bn.contains("river")) {
            int wc = biome.getWaterColor();
            r = (wc >> 16) & 0xFF; g = (wc >> 8) & 0xFF; b = wc & 0xFF;
            if (bn.contains("deep")) { r = (int)(r*0.6f); g = (int)(g*0.6f); b = (int)(b*0.6f); }
            else { r = (int)(r*0.75f); g = (int)(g*0.75f); b = (int)(b*0.75f); }
        } else if (bn.contains("beach") || bn.contains("desert")) { r=190; g=175; b=130; }
        else if (bn.contains("badlands") || bn.contains("eroded")) { r=180; g=95; b=35; }
        else if (bn.contains("snowy") || bn.contains("frozen") || bn.contains("ice")) { r=200; g=205; b=210; }
        else if (bn.contains("mushroom")) { r=130; g=95; b=130; }
        else {
            int gc = getServerSafeGrassColor(bn);
            r=(gc>>16)&0xFF; g=(gc>>8)&0xFF; b=gc&0xFF;
        }

        r=(int)(r*0.65f); g=(int)(g*0.65f); b=(int)(b*0.65f);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int biomeOnlyColor(Holder<Biome> bh) {
        String n = bh.unwrapKey().map(k -> k.location().getPath()).orElse("");
        Biome bi = bh.value();
        int r, g, b;
        if (n.contains("ocean") || n.contains("river")) {
            int wc = bi.getWaterColor(); r=(wc>>16)&0xFF; g=(wc>>8)&0xFF; b=wc&0xFF;
            if (n.contains("deep")) { r=(int)(r*0.6f); g=(int)(g*0.6f); b=(int)(b*0.6f); }
            else { r=(int)(r*0.75f); g=(int)(g*0.75f); b=(int)(b*0.75f); }
        } else if (n.contains("beach") || n.contains("desert")) { r=190; g=175; b=130; }
        else if (n.contains("badlands")) { r=180; g=95; b=35; }
        else if (n.contains("snowy") || n.contains("frozen") || n.contains("ice")) { r=200; g=205; b=210; }
        else { int gc=getServerSafeGrassColor(n); r=(gc>>16)&0xFF; g=(gc>>8)&0xFF; b=gc&0xFF; }
        r=(int)(r*0.65f); g=(int)(g*0.65f); b=(int)(b*0.65f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int getServerSafeGrassColor(String biomeName) {
        if (biomeName.contains("swamp"))                  return 0x6A7039;
        if (biomeName.contains("jungle"))                 return 0x59C93C;
        if (biomeName.contains("dark_forest"))            return 0x507A32;
        if (biomeName.contains("old_growth_spruce"))      return 0x6B8E52;
        if (biomeName.contains("old_growth_pine"))        return 0x6B8E52;
        if (biomeName.contains("old_growth_birch"))       return 0x7DB05A;
        if (biomeName.contains("birch"))                  return 0x88BB67;
        if (biomeName.contains("flower_forest"))          return 0x79C05A;
        if (biomeName.contains("forest"))                 return 0x79C05A;
        if (biomeName.contains("taiga"))                  return 0x6B8E52;
        if (biomeName.contains("savanna"))                return 0xBFB755;
        if (biomeName.contains("windswept_hills"))        return 0x6B935B;
        if (biomeName.contains("windswept"))              return 0x6B935B;
        if (biomeName.contains("meadow"))                 return 0x83BB6D;
        if (biomeName.contains("cherry"))                 return 0xB6DB61;
        if (biomeName.contains("mangrove"))               return 0x6A7039;
        if (biomeName.contains("stony"))                  return 0x6B935B;
        if (biomeName.contains("grove"))                  return 0x6B8E52;
        if (biomeName.contains("plains"))                 return 0x91BD59;
        if (biomeName.contains("sunflower"))              return 0x91BD59;
        return 0x7ABD52;
    }

    private byte[] convertToPng() {
        try {
            BufferedImage img = new BufferedImage(CACHE_SIZE, CACHE_SIZE, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, CACHE_SIZE, CACHE_SIZE, pixels, 0, CACHE_SIZE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            LOGGER.error("[ATA] Failed to convert radar map to PNG", e);
            return null;
        }
    }

    private Path getCacheDir(ServerLevel level) {
        Path worldPath = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldPath.resolve("ata_radar_cache");
    }

    private byte[] tryLoadFromFile(ServerLevel level) {
        try {
            Path file = getCacheDir(level).resolve("radar_v" + CACHE_VERSION + "_" + radarPos.getX() + "_" + radarPos.getZ() + ".png");
            if (Files.exists(file)) {
                LOGGER.info("[ATA] Loading cached radar map from {}", file);
                return Files.readAllBytes(file);
            }
        } catch (Exception e) {
            LOGGER.warn("[ATA] Failed to load cached radar map", e);
        }
        return null;
    }

    private void saveToDisk(ServerLevel level) {
        if (pngBytes == null) return;
        try {
            Path dir = getCacheDir(level);
            Files.createDirectories(dir);
            Path file = dir.resolve("radar_v" + CACHE_VERSION + "_" + radarPos.getX() + "_" + radarPos.getZ() + ".png");
            Files.write(file, pngBytes);
            LOGGER.info("[ATA] Saved radar map to {}", file);
        } catch (Exception e) {
            LOGGER.error("[ATA] Failed to save radar map", e);
        }
    }

    public static int getCacheSize() { return CACHE_SIZE; }
    public static int getCacheBpp() { return CACHE_BPP; }
}
