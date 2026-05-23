package com.synarsis.airtacticalarsenal.client.radar;

import com.synarsis.airtacticalarsenal.radar.ServerRouteMapScanner;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RouteTerrainRenderer {

    private static final int TEX_SIZE = ServerRouteMapScanner.CACHE_SIZE; 
    private static final int DARK = nativeColor(12, 14, 18);

    private NativeImage cacheImage;
    private boolean cacheReady = false;

    private final int centerX, centerZ;

    private DynamicTexture displayTexture;
    private ResourceLocation textureLocation;
    private boolean textureRegistered = false;

    private ByteArrayOutputStream pngAssembly;
    private int expectedChunks = 0;
    private int receivedChunks = 0;

    private float serverBuildProgress = 0;
    private boolean serverBuilding = false;
    private int serverQueuePosition = 0;

    private static RouteTerrainRenderer currentInstance;

    private static final int REUSE_RADIUS = 500;
    private static final int MAX_CACHED_MAPS = 8;
    private static final List<CachedMapEntry> mapCache = new ArrayList<>();

    private static class CachedMapEntry {
        final byte[] pngBytes;
        final int centerX, centerZ;
        CachedMapEntry(byte[] png, int cx, int cz) {
            this.pngBytes = png; this.centerX = cx; this.centerZ = cz;
        }
    }

    private static CachedMapEntry findNearestCache(int x, int z) {
        CachedMapEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (CachedMapEntry e : mapCache) {
            double dx = e.centerX - x;
            double dz = e.centerZ - z;
            double dist = dx * dx + dz * dz;
            if (dist <= (long) REUSE_RADIUS * REUSE_RADIUS && dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    public static boolean hasCachedDataNear(int x, int z) {
        return findNearestCache(x, z) != null;
    }

    public static int getCachedCenterX() {
        CachedMapEntry e = mapCache.isEmpty() ? null : mapCache.get(mapCache.size() - 1);
        return e != null ? e.centerX : 0;
    }
    public static int getCachedCenterZ() {
        CachedMapEntry e = mapCache.isEmpty() ? null : mapCache.get(mapCache.size() - 1);
        return e != null ? e.centerZ : 0;
    }

    public static int getCachedCenterXNear(int x, int z) {
        CachedMapEntry e = findNearestCache(x, z);
        return e != null ? e.centerX : 0;
    }
    public static int getCachedCenterZNear(int x, int z) {
        CachedMapEntry e = findNearestCache(x, z);
        return e != null ? e.centerZ : 0;
    }

    public static void clearCache() {
        mapCache.clear();
    }

    public RouteTerrainRenderer(int centerX, int centerZ) {
        this.centerX = centerX;
        this.centerZ = centerZ;

        displayTexture = new DynamicTexture(TEX_SIZE, TEX_SIZE, false);
        NativeImage d = displayTexture.getPixels();
        if (d != null)
            for (int y = 0; y < TEX_SIZE; y++)
                for (int x = 0; x < TEX_SIZE; x++)
                    d.setPixelRGBA(x, y, DARK);
        displayTexture.upload();
        textureLocation = Minecraft.getInstance().getTextureManager()
                .register("ata_route_terrain", displayTexture);
        textureRegistered = true;
        currentInstance = this;

        CachedMapEntry cached = findNearestCache(centerX, centerZ);
        if (cached != null) {
            loadFromPngBytes(cached.pngBytes);
        }
    }

    public boolean isBuilding() { return serverBuilding; }
    public boolean isQueued() { return serverQueuePosition > 0; }
    public int getQueuePosition() { return serverQueuePosition; }
    public float getBuildProgress() { return serverBuildProgress; }
    public boolean hasCacheData() { return cacheReady; }
    public ResourceLocation getTextureLocation() { return textureLocation; }
    public boolean isReady() { return textureRegistered && textureLocation != null; }
    public int getTextureSize() { return TEX_SIZE; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }

    public int getWorldRadius() {
        return (TEX_SIZE / 2) * ServerRouteMapScanner.CACHE_BPP;
    }

    public static void handleServerData(BlockPos centerPos, int status, float progress,
                                        int chunkIndex, int totalChunks, byte[] data) {
        RouteTerrainRenderer inst = currentInstance;
        if (inst == null) return;

        double dx = inst.centerX - centerPos.getX();
        double dz = inst.centerZ - centerPos.getZ();
        if (dx * dx + dz * dz > (long) REUSE_RADIUS * REUSE_RADIUS) return;

        if (status == 2) {

            inst.serverQueuePosition = Math.max(1, (int) progress);
            inst.serverBuilding = false;
        } else if (status == 0) {

            inst.serverQueuePosition = 0;
            inst.serverBuilding = true;
            inst.serverBuildProgress = progress;
        } else if (status == 1 && data != null) {

            inst.serverBuilding = false;
            inst.serverBuildProgress = 1.0f;
            if (chunkIndex == 0) {
                inst.pngAssembly = new ByteArrayOutputStream();
                inst.expectedChunks = totalChunks;
                inst.receivedChunks = 0;
            }
            if (inst.pngAssembly != null) {
                inst.pngAssembly.write(data, 0, data.length);
                inst.receivedChunks++;
                if (inst.receivedChunks >= inst.expectedChunks) {
                    inst.loadFromPngBytes(inst.pngAssembly.toByteArray());
                    inst.pngAssembly = null;
                }
            }
        }
    }

    private void loadFromPngBytes(byte[] pngBytes) {
        try {
            NativeImage serverImg = NativeImage.read(new ByteArrayInputStream(pngBytes));
            if (serverImg.getWidth() != TEX_SIZE || serverImg.getHeight() != TEX_SIZE) {
                serverImg.close();
                return;
            }
            if (cacheImage != null) cacheImage.close();
            cacheImage = serverImg;
            cacheReady = true;

            CachedMapEntry existing = findNearestCache(centerX, centerZ);
            if (existing != null) {
                mapCache.remove(existing);
            }
            mapCache.add(new CachedMapEntry(pngBytes, centerX, centerZ));
            while (mapCache.size() > MAX_CACHED_MAPS) {
                mapCache.remove(0);
            }

            NativeImage d = displayTexture.getPixels();
            if (d != null) {
                for (int y = 0; y < TEX_SIZE; y++) {
                    for (int x = 0; x < TEX_SIZE; x++) {
                        d.setPixelRGBA(x, y, cacheImage.getPixelRGBA(x, y));
                    }
                }
                displayTexture.upload();
            }
        } catch (Exception e) {

        }
    }

    private static int nativeColor(int r, int g, int b) {
        return (255 << 24) | (b << 16) | (g << 8) | r;
    }

    public void close() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
            textureLocation = null;
        }
        if (displayTexture != null) {
            displayTexture.close();
            displayTexture = null;
        }
        if (cacheImage != null) {
            cacheImage.close();
            cacheImage = null;
        }
        textureRegistered = false;
        if (currentInstance == this) currentInstance = null;
    }
}
