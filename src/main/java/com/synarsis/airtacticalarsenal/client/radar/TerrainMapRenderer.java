package com.synarsis.airtacticalarsenal.client.radar;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@OnlyIn(Dist.CLIENT)
public class TerrainMapRenderer {

    public static final int CACHE_SIZE = 1536;
    public static final int CACHE_BPP = 2;
    private static final int DARK = nativeColor(12, 14, 18);

    private NativeImage cacheImage;
    private boolean cacheReady = false;

    private final int cacheOriginX, cacheOriginZ;
    private final int radarX, radarZ;

    private DynamicTexture displayTexture;
    private ResourceLocation textureLocation;
    private boolean textureRegistered = false;

    private ByteArrayOutputStream pngAssembly;
    private int expectedChunks = 0;
    private int receivedChunks = 0;

    private float serverBuildProgress = 0;
    private boolean serverBuilding = false;
    private int serverQueuePosition = 0;

    private static TerrainMapRenderer currentInstance;

    private static byte[] cachedPngBytes;
    private static int cachedRadarX, cachedRadarZ;

    public TerrainMapRenderer(int radarX, int radarZ) {
        this.radarX = radarX;
        this.radarZ = radarZ;
        this.cacheOriginX = radarX - (CACHE_SIZE / 2) * CACHE_BPP;
        this.cacheOriginZ = radarZ - (CACHE_SIZE / 2) * CACHE_BPP;

        displayTexture = new DynamicTexture(CACHE_SIZE, CACHE_SIZE, false);
        NativeImage d = displayTexture.getPixels();
        if (d != null)
            for (int y = 0; y < CACHE_SIZE; y++)
                for (int x = 0; x < CACHE_SIZE; x++)
                    d.setPixelRGBA(x, y, DARK);
        displayTexture.upload();
        textureLocation = Minecraft.getInstance().getTextureManager()
                .register("ata_radar_terrain", displayTexture);
        textureRegistered = true;
        currentInstance = this;

        if (cachedPngBytes != null && cachedRadarX == radarX && cachedRadarZ == radarZ) {
            loadFromPngBytes(cachedPngBytes);
        }
    }

    public boolean isBuilding() { return serverBuilding; }
    public boolean isQueued() { return serverQueuePosition > 0; }
    public int getQueuePosition() { return serverQueuePosition; }
    public float getBuildProgress() { return serverBuildProgress; }

    public static void handleServerData(BlockPos radarPos, int status, float progress,
                                        int chunkIndex, int totalChunks, byte[] data) {
        TerrainMapRenderer inst = currentInstance;
        if (inst == null) return;
        if (inst.radarX != radarPos.getX() || inst.radarZ != radarPos.getZ()) return;

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
            if (serverImg.getWidth() != CACHE_SIZE || serverImg.getHeight() != CACHE_SIZE) {
                serverImg.close();
                return;
            }
            if (cacheImage != null) cacheImage.close();
            cacheImage = serverImg;
            cacheReady = true;

            cachedPngBytes = pngBytes;
            cachedRadarX = radarX;
            cachedRadarZ = radarZ;

            NativeImage d = displayTexture.getPixels();
            if (d != null) {
                for (int y = 0; y < CACHE_SIZE; y++) {
                    for (int x = 0; x < CACHE_SIZE; x++) {
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

    public ResourceLocation getTextureLocation() { return textureLocation; }
    public boolean isReady() { return textureRegistered && textureLocation != null; }
    public boolean hasCacheData() { return cacheReady; }
    public int getTextureSize() { return CACHE_SIZE; }
    public int getRadarX() { return radarX; }
    public int getRadarZ() { return radarZ; }
    public int getCacheOriginX() { return cacheOriginX; }
    public int getCacheOriginZ() { return cacheOriginZ; }
    public int getCacheWorldRadius() { return (CACHE_SIZE / 2) * CACHE_BPP; }

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

    public void forceRebuild() {
        cacheReady = false;
        if (cacheImage != null) {
            cacheImage.close();
            cacheImage = null;
        }
    }
}
