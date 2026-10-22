package com.synarsis.airtacticalarsenal.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

import javax.annotation.Nullable;
import java.util.function.IntBinaryOperator;

public final class LightLevelCopy {

    public static final int SECTION_BLOCKS = 4096;

    private LightLevelCopy() {
    }

    public static int pack(int sky, int block) {
        return (sky & 15) << 4 | block & 15;
    }

    public static void copy(@Nullable DataLayer sky, @Nullable DataLayer block, boolean hasSky,
            IntBinaryOperator skyColumn, byte[] out) {
        if (sky == null) {
            copyColumns(hasSky, skyColumn, out);
        } else {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        out[(y * 16 + z) * 16 + x] = (byte) (sky.get(x, y, z) << 4);
                    }
                }
            }
        }
        if (block != null) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = (y * 16 + z) * 16 + x;
                        out[index] |= (byte) block.get(x, y, z);
                    }
                }
            }
        }
    }

    private static void copyColumns(boolean hasSky, IntBinaryOperator skyColumn, byte[] out) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                byte value = hasSky ? (byte) ((skyColumn.applyAsInt(x, z) & 15) << 4) : 0;
                for (int y = 0; y < 16; y++) {
                    out[(y * 16 + z) * 16 + x] = value;
                }
            }
        }
    }

    public static void read(Level level, int sectionX, int sectionY, int sectionZ, byte[] out) {
        SectionPos section = SectionPos.of(sectionX, sectionY, sectionZ);
        var engine = level.getLightEngine();
        boolean hasSky = level.dimensionType().hasSkyLight();
        DataLayer sky = hasSky ? engine.getLayerListener(LightLayer.SKY).getDataLayerData(section) : null;
        DataLayer block = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(section);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int baseX = sectionX * 16;
        int baseY = sectionY * 16;
        int baseZ = sectionZ * 16;
        copy(sky, block, hasSky, (x, z) -> level.getBrightness(LightLayer.SKY, position.set(baseX + x, baseY, baseZ + z)),
                out);
    }
}
