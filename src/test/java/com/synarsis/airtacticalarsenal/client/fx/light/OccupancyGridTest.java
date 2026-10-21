package com.synarsis.airtacticalarsenal.client.fx.light;

import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The solidity volume: ring addressing and the layout of layers (SPEC section 15.8)")
class OccupancyGridTest {

    private static byte[] section(int sx, int sy, int sz) {
        byte[] out = new byte[4096];
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    out[(ly * 16 + lz) * 16 + lx] = (byte) value(sx * 16 + lx, sy * 16 + ly, sz * 16 + lz);
                }
            }
        }
        return out;
    }

    private static int value(int x, int y, int z) {
        return 1 + Math.floorMod(x * 31 + y * 17 + z * 7, 255);
    }

    private static final class Mirror implements OccupancyGrid.TextureSink {
        final byte[] texels = new byte[OccupancyGrid.TEXTURE_SIZE * OccupancyGrid.TEXTURE_SIZE];

        @Override
        public void layer(int u, int v, ByteBuffer pixels) {
            for (int row = 0; row < 16; row++) {
                for (int col = 0; col < 16; col++) {
                    this.texels[(v + row) * OccupancyGrid.TEXTURE_SIZE + u + col] = pixels.get(row * 16 + col);
                }
            }
        }

        int at(int x, int y, int z) {
            return this.texels[OccupancyGrid.texV(z, y) * OccupancyGrid.TEXTURE_SIZE + OccupancyGrid.texU(x, y)] & 0xFF;
        }
    }

    private static void move(OccupancyGrid grid, Mirror card, double x, double y, double z) {
        for (OccupancyGrid.Slot slot : grid.recenter(x, y, z)) {
            grid.zero(slot, card);
        }
    }

    private static void readAll(OccupancyGrid grid, Mirror card) {
        int osx = grid.originX() >> 4;
        int osy = grid.originY() >> 4;
        int osz = grid.originZ() >> 4;
        for (int dy = 0; dy < OccupancyGrid.SECTIONS_Y; dy++) {
            for (int dz = 0; dz < OccupancyGrid.SECTIONS_Z; dz++) {
                for (int dx = 0; dx < OccupancyGrid.SECTIONS_X; dx++) {
                    grid.writeSection(osx + dx, osy + dy, osz + dz, section(osx + dx, osy + dy, osz + dz), card);
                }
            }
        }
    }

    private static void assertMirrors(OccupancyGrid grid, Mirror card, String when) {
        for (int dy = 0; dy < OccupancyGrid.SIZE_Y; dy++) {
            for (int dz = 0; dz < OccupancyGrid.SIZE_Z; dz++) {
                for (int dx = 0; dx < OccupancyGrid.SIZE_X; dx++) {
                    int x = grid.originX() + dx;
                    int y = grid.originY() + dy;
                    int z = grid.originZ() + dz;
                    if (grid.opacity(x, y, z) != card.at(x, y, z)) {
                        assertEquals(grid.opacity(x, y, z), card.at(x, y, z),
                                when + ": the card disagrees with the model at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the layout is a 1024 by 1024 tile of 8 by 8 layers of 128 by 128, and no two blocks share a texel")
    void layout() {
        assertEquals(OccupancyGrid.TEXTURE_SIZE, 8 * OccupancyGrid.SIZE_X);
        assertEquals(OccupancyGrid.TEXTURE_SIZE, 8 * OccupancyGrid.SIZE_Z);
        assertEquals(64, 8 * 8);
        boolean[] used = new boolean[OccupancyGrid.TEXTURE_SIZE * OccupancyGrid.TEXTURE_SIZE];
        for (int y = 0; y < 64; y++) {
            for (int z = 0; z < 128; z++) {
                for (int x = 0; x < 128; x++) {
                    int u = OccupancyGrid.texU(x, y);
                    int v = OccupancyGrid.texV(z, y);
                    assertTrue(u >= 0 && u < 1024 && v >= 0 && v < 1024);
                    assertFalse(used[v * 1024 + u], "texel taken twice at " + x + "," + y + "," + z);
                    used[v * 1024 + u] = true;
                }
            }
        }
        assertEquals(5 + 128 * 3, OccupancyGrid.texU(5, 3));
        assertEquals(9 + 128 * 1, OccupancyGrid.texV(9, 11));
        assertEquals(5 + 128 * 7, OccupancyGrid.texU(5, 63));
        assertEquals(9 + 128 * 7, OccupancyGrid.texV(9, 63));
    }

    @Test
    @DisplayName("the addressing is a ring: negative coordinates fold into the volume the same way positive ones do")
    void ring() {
        assertEquals(OccupancyGrid.texU(-1, -1), OccupancyGrid.texU(127, 63));
        assertEquals(OccupancyGrid.texV(-1, -1), OccupancyGrid.texV(127, 63));
        assertEquals(OccupancyGrid.dataIndex(-128, -64, -128), OccupancyGrid.dataIndex(0, 0, 0));
        assertEquals(OccupancyGrid.dataIndex(-1, -1, -1), OccupancyGrid.dataIndex(127, 63, 127));
        assertEquals(OccupancyGrid.slot(-1, -1, -1), OccupancyGrid.slot(7, 3, 7));
        assertEquals(OccupancyGrid.slot(8, 4, 8), OccupancyGrid.slot(0, 0, 0));
        assertEquals(SectionPos.asLong(-3, 5, -7), OccupancyGrid.sectionKey(-3, 5, -7));
        assertEquals(SectionPos.asLong(1234567, -4, 2000000), OccupancyGrid.sectionKey(1234567, -4, 2000000));
    }

    @Test
    @DisplayName("the volume is centred on the camera on the grid of sections")
    void origin() {
        OccupancyGrid grid = new OccupancyGrid();
        grid.recenter(100.3, 70.9, -35.0);
        assertEquals(0, Math.floorMod(grid.originX(), 16));
        assertEquals(0, Math.floorMod(grid.originY(), 16));
        assertEquals(0, Math.floorMod(grid.originZ(), 16));
        assertTrue(grid.contains(100, 70, -35));
        assertTrue(Math.abs(grid.originX() + 64 - 100.3) <= 16);
        assertTrue(Math.abs(grid.originY() + 32 - 70.9) <= 16);
        assertTrue(grid.recenter(105.0, 72.0, -34.0).isEmpty(), "within the same section nothing changes hands");
        assertFalse(grid.recenter(117.0, 72.0, -34.0).isEmpty(), "into the next section a column of slots does");
    }

    @Test
    @DisplayName("moving one section gives away exactly the slots of the column that left: 32 for a step in X")
    void slotsThatChangeHands() {
        OccupancyGrid grid = new OccupancyGrid();
        grid.recenter(5.0, 40.0, 5.0);
        List<OccupancyGrid.Slot> step = grid.recenter(5.0 + 16.0, 40.0, 5.0);
        assertEquals(OccupancyGrid.SECTIONS_Y * OccupancyGrid.SECTIONS_Z, step.size());
        int newColumn = (grid.originX() >> 4) + OccupancyGrid.SECTIONS_X - 1;
        for (OccupancyGrid.Slot slot : step) {
            assertEquals(newColumn, slot.sx(), "each is for a section of the column that came in");
            assertEquals(slot.index(), OccupancyGrid.slot(slot.sx(), slot.sy(), slot.sz()));
        }
        List<OccupancyGrid.Slot> more = grid.recenter(5.0 + 48.0, 40.0 - 16.0, 5.0);
        int kept = (OccupancyGrid.SECTIONS_X - 2) * (OccupancyGrid.SECTIONS_Y - 1) * OccupancyGrid.SECTIONS_Z;
        assertEquals(OccupancyGrid.SECTION_COUNT - kept, more.size());
        assertEquals(OccupancyGrid.SECTION_COUNT, grid.recenter(5000.0, 40.0, 5000.0).size());
    }

    @Test
    @DisplayName("each block of a written section is read back from the texture by the shader's own index, negatives included")
    void writeAndRead() {
        Mirror card = new Mirror();
        OccupancyGrid grid = new OccupancyGrid();
        move(grid, card, -1000.5, 10.0, -77.5);
        readAll(grid, card);
        int ox = grid.originX();
        int oy = grid.originY();
        int oz = grid.originZ();
        for (int dy = 0; dy < 64; dy++) {
            for (int dz = 0; dz < 128; dz++) {
                for (int dx = 0; dx < 128; dx++) {
                    int x = ox + dx;
                    int y = oy + dy;
                    int z = oz + dz;
                    assertEquals(value(x, y, z), grid.opacity(x, y, z), "grid at " + x + "," + y + "," + z);
                    assertEquals(value(x, y, z), card.at(x, y, z), "texture at " + x + "," + y + "," + z);
                }
            }
        }
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int cx = random.nextInt(128);
            int cy = random.nextInt(64);
            int cz = random.nextInt(128);
            int rx = (cx + Math.floorMod(ox, 128)) & 127;
            int ry = (cy + Math.floorMod(oy, 64)) & 63;
            int rz = (cz + Math.floorMod(oz, 128)) & 127;
            int u = rx + 128 * (ry & 7);
            int v = rz + 128 * (ry >> 3);
            assertEquals(value(ox + cx, oy + cy, oz + cz), card.texels[v * 1024 + u] & 0xFF);
        }
        assertEquals(0, grid.opacity(ox - 1, oy, oz));
        assertEquals(0, grid.opacity(ox, oy + 64, oz));
        assertEquals(0, grid.opacity(ox, oy, oz + 128));
    }

    @Test
    @DisplayName("after the volume moves, a slot not yet read is free on the card too, not the old section's stone")
    void afterAShift() {
        Mirror card = new Mirror();
        OccupancyGrid grid = new OccupancyGrid();
        move(grid, card, 5.0, 40.0, 5.0);
        readAll(grid, card);
        assertMirrors(grid, card, "filled");
        move(grid, card, 5.0 + 32.0, 40.0 - 16.0, 5.0);
        assertMirrors(grid, card, "just moved");
        int osx = grid.originX() >> 4;
        int osy = grid.originY() >> 4;
        int osz = grid.originZ() >> 4;
        int stale = 0;
        for (int dy = 0; dy < OccupancyGrid.SECTIONS_Y; dy++) {
            for (int dz = 0; dz < OccupancyGrid.SECTIONS_Z; dz++) {
                for (int dx = 0; dx < OccupancyGrid.SECTIONS_X; dx++) {
                    if (!grid.holds(osx + dx, osy + dy, osz + dz)) {
                        stale++;
                    }
                }
            }
        }
        int expected = OccupancyGrid.SECTION_COUNT
                - (OccupancyGrid.SECTIONS_X - 2) * (OccupancyGrid.SECTIONS_Y - 1) * OccupancyGrid.SECTIONS_Z;
        assertEquals(expected, stale);
        int kept = 0;
        for (int dy = 0; dy < 64; dy += 5) {
            for (int dz = 0; dz < 128; dz += 3) {
                for (int dx = 0; dx < 128; dx += 3) {
                    int x = grid.originX() + dx;
                    int y = grid.originY() + dy;
                    int z = grid.originZ() + dz;
                    if (grid.holds(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))) {
                        assertEquals(value(x, y, z), card.at(x, y, z));
                        kept++;
                    } else {
                        assertEquals(0, card.at(x, y, z), "a slot not yet read is free on the card as well");
                    }
                }
            }
        }
        assertTrue(kept > 1000);
        for (int dy = 0; dy < OccupancyGrid.SECTIONS_Y; dy++) {
            for (int dz = 0; dz < OccupancyGrid.SECTIONS_Z; dz++) {
                for (int dx = 0; dx < OccupancyGrid.SECTIONS_X; dx++) {
                    int sx = osx + dx;
                    int sy = osy + dy;
                    int sz = osz + dz;
                    if (!grid.holds(sx, sy, sz)) {
                        grid.writeSection(sx, sy, sz, section(sx, sy, sz), card);
                    }
                }
            }
        }
        assertMirrors(grid, card, "read again");
        for (int dy = 0; dy < 64; dy += 7) {
            for (int dz = 0; dz < 128; dz += 5) {
                for (int dx = 0; dx < 128; dx += 5) {
                    int x = grid.originX() + dx;
                    int y = grid.originY() + dy;
                    int z = grid.originZ() + dz;
                    assertEquals(value(x, y, z), card.at(x, y, z));
                }
            }
        }
    }

    @Test
    @DisplayName("after any walk of the camera, with sections read in whatever order and amount, the card equals the model in every cell")
    void anyWalk() {
        Random random = new Random(20261021);
        Mirror card = new Mirror();
        OccupancyGrid grid = new OccupancyGrid();
        double x = 0.0;
        double y = 70.0;
        double z = 0.0;
        move(grid, card, x, y, z);
        for (int step = 0; step < 24; step++) {
            double reach = random.nextInt(4) == 0 ? 200.0 : 24.0;
            x += (random.nextDouble() - 0.5) * 2.0 * reach;
            y += (random.nextDouble() - 0.5) * 2.0 * reach * 0.5;
            z += (random.nextDouble() - 0.5) * 2.0 * reach;
            move(grid, card, x, y, z);
            assertMirrors(grid, card, "step " + step + " just moved");
            int osx = grid.originX() >> 4;
            int osy = grid.originY() >> 4;
            int osz = grid.originZ() >> 4;
            int reads = random.nextInt(40);
            for (int i = 0; i < reads; i++) {
                int sx = osx + random.nextInt(OccupancyGrid.SECTIONS_X);
                int sy = osy + random.nextInt(OccupancyGrid.SECTIONS_Y);
                int sz = osz + random.nextInt(OccupancyGrid.SECTIONS_Z);
                grid.writeSection(sx, sy, sz, section(sx, sy, sz), card);
            }
            assertMirrors(grid, card, "step " + step + " after " + reads + " reads");
        }
    }
}
