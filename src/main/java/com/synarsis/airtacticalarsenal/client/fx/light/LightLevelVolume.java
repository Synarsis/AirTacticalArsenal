package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import com.synarsis.airtacticalarsenal.lighting.LightLevelCopy;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class LightLevelVolume {
    private static final LightLevelUpdates UPDATES = new LightLevelUpdates();
    private static final byte[] SECTION = new byte[4096];
    private static OccupancyGrid grid;
    private static ClientLevel world;
    private static int texture;

    private LightLevelVolume() {
    }

    public static int pack(int sky, int block) {
        return LightLevelCopy.pack(sky, block);
    }

    public static int texture() {
        return texture;
    }

    public static void markChanged(BlockPos position) {
        if (world != null) {
            UPDATES.changed(position.getX() >> 4, position.getY() >> 4, position.getZ() >> 4, world.getGameTime());
        }
    }

    static void loaded(int sectionX, int sectionY, int sectionZ) {
        UPDATES.loaded(sectionX, sectionY, sectionZ);
    }

    public static void release() {
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
        }
        texture = 0;
        grid = null;
        world = null;
        UPDATES.clear();
    }

    static void update(ClientLevel level, Vec3 camera) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        TexUpload.Store store = TexUpload.begin();
        try {
            if (texture == 0 || world != level) {
                if (world != level && world != null) {
                    release();
                }
                world = level;
                grid = new OccupancyGrid();
                texture = GL11.glGenTextures();
                GlStateManager._bindTexture(texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, OccupancyGrid.TEXTURE_SIZE,
                        OccupancyGrid.TEXTURE_SIZE, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE,
                        BufferUtils.createByteBuffer(OccupancyGrid.TEXTURE_SIZE * OccupancyGrid.TEXTURE_SIZE));
            }
            GlStateManager._bindTexture(texture);
            for (OccupancyGrid.Slot slot : grid.recenter(camera.x, camera.y, camera.z)) {
                grid.zero(slot, LightLevelVolume::upload);
                UPDATES.loaded(slot.sx(), slot.sy(), slot.sz());
            }
            for (long key : UPDATES.take(level.getGameTime(), OccupancyVolume.SECTIONS_PER_FRAME)) {
                int sectionX = (int) (key >> 42);
                int sectionY = (int) (key << 44 >> 44);
                int sectionZ = (int) (key << 22 >> 42);
                if (grid.containsSection(sectionX, sectionY, sectionZ)) {
                    read(level, sectionX, sectionY, sectionZ);
                }
            }
            int round = UPDATES.roundDue(System.nanoTime());
            for (int index = 0; index < round; index++) {
                int slot = UPDATES.nextRound();
                int sectionX = (grid.originX() >> 4) + slot % OccupancyGrid.SECTIONS_X;
                int sectionZ = (grid.originZ() >> 4) + slot / OccupancyGrid.SECTIONS_X % OccupancyGrid.SECTIONS_Z;
                int sectionY = (grid.originY() >> 4) + slot / (OccupancyGrid.SECTIONS_X * OccupancyGrid.SECTIONS_Z);
                read(level, sectionX, sectionY, sectionZ);
            }
        } finally {
            TexUpload.end(store);
            GlStateManager._bindTexture(previous);
        }
    }

    private static void read(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
        boolean loaded = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false) != null;
        if (loaded) {
            LightLevelCopy.read(level, sectionX, sectionY, sectionZ, SECTION);
        } else {
            java.util.Arrays.fill(SECTION, (byte) 0);
        }
        grid.writeSection(sectionX, sectionY, sectionZ, SECTION, LightLevelVolume::upload);
    }

    private static void upload(int horizontal, int vertical, ByteBuffer pixels) {
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, horizontal, vertical, 16, 16, GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE, pixels);
    }
}
