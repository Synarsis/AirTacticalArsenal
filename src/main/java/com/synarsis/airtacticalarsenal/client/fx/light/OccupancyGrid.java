package com.synarsis.airtacticalarsenal.client.fx.light;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OccupancyGrid {

    public static final int SIZE_X = 128;
    public static final int SIZE_Y = 64;
    public static final int SIZE_Z = 128;
    public static final int TEXTURE_SIZE = 1024;
    public static final int SECTIONS_X = SIZE_X / 16;
    public static final int SECTIONS_Y = SIZE_Y / 16;
    public static final int SECTIONS_Z = SIZE_Z / 16;
    public static final int SECTION_COUNT = SECTIONS_X * SECTIONS_Y * SECTIONS_Z;
    public static final long NONE = Long.MIN_VALUE;

    @FunctionalInterface
    public interface TextureSink {
        void layer(int u, int v, ByteBuffer pixels);
    }

    public record Slot(int index, int sx, int sy, int sz) {
    }

    private final byte[] data = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    private final long[] slotKey = new long[SECTION_COUNT];
    private final long[] slotFor = new long[SECTION_COUNT];
    private int originX;
    private int originY;
    private int originZ;

    public OccupancyGrid() {
        Arrays.fill(this.slotKey, NONE);
        Arrays.fill(this.slotFor, NONE);
        recenter(0.0, 0.0, 0.0);
    }

    public static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) sy & 0xFFFFFL) | (((long) sz & 0x3FFFFFL) << 20);
    }

    public static int slot(int sx, int sy, int sz) {
        return (Math.floorMod(sy, SECTIONS_Y) * SECTIONS_Z + Math.floorMod(sz, SECTIONS_Z)) * SECTIONS_X
                + Math.floorMod(sx, SECTIONS_X);
    }

    public static int dataIndex(int x, int y, int z) {
        return (Math.floorMod(y, SIZE_Y) * SIZE_Z + Math.floorMod(z, SIZE_Z)) * SIZE_X + Math.floorMod(x, SIZE_X);
    }

    public static int texU(int x, int y) {
        return Math.floorMod(x, SIZE_X) + SIZE_X * (Math.floorMod(y, SIZE_Y) % 8);
    }

    public static int texV(int z, int y) {
        return Math.floorMod(z, SIZE_Z) + SIZE_Z * (Math.floorMod(y, SIZE_Y) / 8);
    }

    public List<Slot> recenter(double camX, double camY, double camZ) {
        int ox = Math.floorDiv((int) Math.floor(camX), 16) * 16 - SIZE_X / 2;
        int oy = Math.floorDiv((int) Math.floor(camY), 16) * 16 - SIZE_Y / 2;
        int oz = Math.floorDiv((int) Math.floor(camZ), 16) * 16 - SIZE_Z / 2;
        if (ox == this.originX && oy == this.originY && oz == this.originZ && this.slotFor[0] != NONE) {
            return List.of();
        }
        this.originX = ox;
        this.originY = oy;
        this.originZ = oz;
        List<Slot> changed = new ArrayList<>();
        int osx = ox >> 4;
        int osy = oy >> 4;
        int osz = oz >> 4;
        for (int dy = 0; dy < SECTIONS_Y; dy++) {
            for (int dz = 0; dz < SECTIONS_Z; dz++) {
                for (int dx = 0; dx < SECTIONS_X; dx++) {
                    int sx = osx + dx;
                    int sy = osy + dy;
                    int sz = osz + dz;
                    int slot = slot(sx, sy, sz);
                    long key = sectionKey(sx, sy, sz);
                    long before = this.slotFor[slot];
                    this.slotFor[slot] = key;
                    if (before != NONE && before != key) {
                        this.slotKey[slot] = NONE;
                        clearData(sx, sy, sz);
                        changed.add(new Slot(slot, sx, sy, sz));
                    }
                }
            }
        }
        return changed;
    }

    public void zero(Slot slot, TextureSink sink) {
        ByteBuffer zeros = ByteBuffer.allocateDirect(256);
        for (int ly = 0; ly < 16; ly++) {
            int y = slot.sy() * 16 + ly;
            zeros.clear();
            sink.layer(texU(slot.sx() * 16, y), texV(slot.sz() * 16, y), zeros);
        }
    }

    private void clearData(int sx, int sy, int sz) {
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                Arrays.fill(this.data, dataIndex(sx * 16, sy * 16 + ly, sz * 16 + lz),
                        dataIndex(sx * 16, sy * 16 + ly, sz * 16 + lz) + 16, (byte) 0);
            }
        }
    }

    public int originX() {
        return this.originX;
    }

    public int originY() {
        return this.originY;
    }

    public int originZ() {
        return this.originZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.originX && x < this.originX + SIZE_X && y >= this.originY && y < this.originY + SIZE_Y
                && z >= this.originZ && z < this.originZ + SIZE_Z;
    }

    public boolean containsSection(int sx, int sy, int sz) {
        return contains(sx * 16, sy * 16, sz * 16);
    }

    public boolean holds(int sx, int sy, int sz) {
        return this.slotKey[slot(sx, sy, sz)] == sectionKey(sx, sy, sz);
    }

    public int opacity(int x, int y, int z) {
        if (!contains(x, y, z) || !holds(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))) {
            return 0;
        }
        return this.data[dataIndex(x, y, z)] & 0xFF;
    }

    public void writeSection(int sx, int sy, int sz, byte[] blocks, TextureSink sink) {
        writeSection(sx, sy, sz, blocks);
        ByteBuffer layer = ByteBuffer.allocateDirect(256);
        for (int ly = 0; ly < 16; ly++) {
            layerInto(sx, sy, sz, ly, layer);
            int y = sy * 16 + ly;
            sink.layer(texU(sx * 16, y), texV(sz * 16, y), layer);
        }
    }

    void writeSection(int sx, int sy, int sz, byte[] blocks) {
        int x0 = sx * 16;
        int y0 = sy * 16;
        int z0 = sz * 16;
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                System.arraycopy(blocks, (ly * 16 + lz) * 16, this.data, dataIndex(x0, y0 + ly, z0 + lz), 16);
            }
        }
        this.slotKey[slot(sx, sy, sz)] = sectionKey(sx, sy, sz);
    }

    public void layerInto(int sx, int sy, int sz, int ly, ByteBuffer out) {
        int x0 = sx * 16;
        int y = sy * 16 + ly;
        int z0 = sz * 16;
        out.clear();
        for (int lz = 0; lz < 16; lz++) {
            out.put(this.data, dataIndex(x0, y, z0 + lz), 16);
        }
        out.flip();
    }

    public void clear() {
        Arrays.fill(this.data, (byte) 0);
        Arrays.fill(this.slotKey, NONE);
        Arrays.fill(this.slotFor, NONE);
    }
}
