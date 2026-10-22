package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class OccupancyVolume {

    public static final int SECTIONS_PER_FRAME = 24;
    private static final int ROUND_PER_FRAME = 2;

    static final int SOLID = 255;
    static final int PLATE = 200;
    static final int LEAVES = 140;
    private static final double THIN = 0.15;

    private static OccupancyGrid grid;
    private static ClientLevel gridLevel;
    private static int texture;
    private static final LongOpenHashSet CHANGED = new LongOpenHashSet();
    private static final LongOpenHashSet PENDING = new LongOpenHashSet();
    private static int round;
    private static long frame;
    private static long updatedFrame = -1;
    private static boolean broken;

    private static final byte[] SECTION = new byte[4096];

    private OccupancyVolume() {
    }

    public static void beginFrame() {
        frame++;
    }

    public static boolean active() {
        return grid != null && texture != 0;
    }

    public static int texture() {
        return texture;
    }

    public static OccupancyGrid grid() {
        return grid;
    }

    public static void markChanged(BlockPos pos) {
        LightLevelVolume.markChanged(pos);
        if (grid != null) {
            CHANGED.add(OccupancyGrid.sectionKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        }
    }

    public static void release() {
        LightLevelVolume.release();
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
        grid = null;
        gridLevel = null;
        CHANGED.clear();
        PENDING.clear();
        updatedFrame = -1;
    }

    public static boolean updatedThisFrame() {
        return updatedFrame == frame;
    }

    public static void update(ClientLevel level, Vec3 cam, List<Vec3> focus) {
        if (updatedFrame == frame || broken) {
            return;
        }
        updatedFrame = frame;
        if (grid == null || gridLevel != level) {
            release();
            if (!create()) {
                broken = true;
                return;
            }
            gridLevel = level;
            updatedFrame = frame;
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        TexUpload.Store store = TexUpload.begin();
        int budget = SECTIONS_PER_FRAME;
        try {
            for (OccupancyGrid.Slot slot : grid.recenter(cam.x, cam.y, cam.z)) {
                grid.zero(slot, SINK);
            }
            int osx = grid.originX() >> 4;
            int osy = grid.originY() >> 4;
            int osz = grid.originZ() >> 4;
            if (!CHANGED.isEmpty()) {
                LongIterator it = CHANGED.iterator();
                while (it.hasNext() && budget > 0) {
                    long key = it.nextLong();
                    it.remove();
                    int sx = (int) (key >> 42);
                    int sy = (int) (key << 44 >> 44);
                    int sz = (int) (key << 22 >> 42);
                    if (grid.containsSection(sx, sy, sz)) {
                        load(level, sx, sy, sz);
                        budget--;
                    }
                }
                CHANGED.removeIf(key -> !grid.containsSection((int) (key >> 42), (int) (key << 44 >> 44),
                        (int) (key << 22 >> 42)));
            }
            if (budget > 0) {
                LongArrayList stale = new LongArrayList();
                for (int dy = 0; dy < OccupancyGrid.SECTIONS_Y; dy++) {
                    for (int dz = 0; dz < OccupancyGrid.SECTIONS_Z; dz++) {
                        for (int dx = 0; dx < OccupancyGrid.SECTIONS_X; dx++) {
                            if (!grid.holds(osx + dx, osy + dy, osz + dz)) {
                                stale.add(OccupancyGrid.sectionKey(osx + dx, osy + dy, osz + dz));
                            }
                        }
                    }
                }
                if (!stale.isEmpty()) {
                    Long[] order = new Long[stale.size()];
                    for (int i = 0; i < order.length; i++) {
                        order[i] = stale.getLong(i);
                    }
                    java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(
                            (Long key) -> distanceToFocus(key, cam, focus)));
                    for (int i = 0; i < order.length && budget > 0; i++) {
                        long key = order[i];
                        load(level, (int) (key >> 42), (int) (key << 44 >> 44), (int) (key << 22 >> 42));
                        budget--;
                    }
                }
            }
            if (budget > 0 && !PENDING.isEmpty()) {
                LongArrayList ready = new LongArrayList();
                LongIterator it = PENDING.iterator();
                while (it.hasNext()) {
                    long key = it.nextLong();
                    int sx = (int) (key >> 42);
                    int sy = (int) (key << 44 >> 44);
                    int sz = (int) (key << 22 >> 42);
                    if (!grid.containsSection(sx, sy, sz)) {
                        it.remove();
                    } else if (level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false) != null) {
                        ready.add(key);
                    }
                }
                for (int i = 0; i < ready.size() && budget > 0; i++) {
                    long key = ready.getLong(i);
                    load(level, (int) (key >> 42), (int) (key << 44 >> 44), (int) (key << 22 >> 42));
                    budget--;
                }
            }
            for (int i = 0; i < ROUND_PER_FRAME && budget > 0; i++) {
                round = (round + 1) % OccupancyGrid.SECTION_COUNT;
                int dx = round % OccupancyGrid.SECTIONS_X;
                int dz = (round / OccupancyGrid.SECTIONS_X) % OccupancyGrid.SECTIONS_Z;
                int dy = round / (OccupancyGrid.SECTIONS_X * OccupancyGrid.SECTIONS_Z);
                load(level, osx + dx, osy + dy, osz + dz);
                budget--;
            }
        } finally {
            TexUpload.end(store);
            GlStateManager._bindTexture(previousTexture);
        }
        LightLevelVolume.update(level, cam);
    }

    private static double distanceToFocus(long key, Vec3 cam, List<Vec3> focus) {
        double cx = ((int) (key >> 42)) * 16 + 8;
        double cy = ((int) (key << 44 >> 44)) * 16 + 8;
        double cz = ((int) (key << 22 >> 42)) * 16 + 8;
        double best = sq(cx - cam.x) + sq(cy - cam.y) + sq(cz - cam.z);
        for (Vec3 f : focus) {
            best = Math.min(best, sq(cx - f.x) + sq(cy - f.y) + sq(cz - f.z));
        }
        return best;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static boolean create() {
        try {
            int tex = GL11.glGenTextures();
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._bindTexture(tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
            int size = OccupancyGrid.TEXTURE_SIZE;
            ByteBuffer zeros = BufferUtils.createByteBuffer(size * size);
            TexUpload.Store store = TexUpload.begin();
            try {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, size, size, 0, GL11.GL_RED,
                        GL11.GL_UNSIGNED_BYTE, zeros);
            } finally {
                TexUpload.end(store);
            }
            GlStateManager._bindTexture(previous);
            texture = tex;
            grid = new OccupancyGrid();
            return true;
        } catch (RuntimeException e) {
            AirTacticalArsenal.LOGGER.error("The solidity volume of the lights could not be made", e);
            release();
            return false;
        }
    }

    private static void load(ClientLevel level, int sx, int sy, int sz) {
        LightLevelVolume.loaded(sx, sy, sz);
        boolean chunkThere = read(level, sx, sy, sz);
        long key = OccupancyGrid.sectionKey(sx, sy, sz);
        if (chunkThere) {
            PENDING.remove(key);
        } else {
            PENDING.add(key);
        }
        grid.writeSection(sx, sy, sz, SECTION, SINK);
    }

    private static final OccupancyGrid.TextureSink SINK = (u, v, pixels) -> {
        if (!TexUpload.fits(pixels, 16, 16)) {
            AirTacticalArsenal.LOGGER.warn("The light grid layer is not a direct 16x16 buffer; it is skipped");
            return;
        }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, u, v, 16, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, pixels);
    };

    private static boolean read(ClientLevel level, int sx, int sy, int sz) {
        java.util.Arrays.fill(SECTION, (byte) 0);
        LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }
        if (sy < level.getMinSection() || sy >= level.getMaxSection()) {
            return true;
        }
        LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
        if (section.hasOnlyAir()) {
            return true;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = section.getBlockState(lx, ly, lz);
                    if (!state.isAir()) {
                        pos.set(sx * 16 + lx, sy * 16 + ly, sz * 16 + lz);
                        SECTION[(ly * 16 + lz) * 16 + lx] = (byte) classify(state, chunk, pos);
                    }
                }
            }
        }
        return true;
    }

    static int classify(BlockState state, LevelChunk chunk, BlockPos pos) {
        if (state.isSolidRender(chunk, pos)) {
            return SOLID;
        }
        if (state.is(BlockTags.LEAVES)) {
            return LEAVES;
        }
        if (state.canOcclude()) {
            VoxelShape shape = state.getOcclusionShape(chunk, pos);
            if (!shape.isEmpty()) {
                var box = shape.bounds();
                if (box.getXsize() * box.getYsize() * box.getZsize() >= THIN) {
                    return PLATE;
                }
            }
        }
        return 0;
    }
}
