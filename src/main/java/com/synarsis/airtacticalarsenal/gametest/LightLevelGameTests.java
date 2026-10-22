package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.lighting.LightLevelCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder(AirTacticalArsenal.MODID)
@PrefixGameTestTemplate(false)
public final class LightLevelGameTests {

    private static final String TEMPLATE = "flat_wide";

    private LightLevelGameTests() {
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "lightLevels", timeoutTicks = 200)
    public static void sectionCopyMatchesTheEngineUnderARoof(GameTestHelper helper) {
        for (int x = 20; x <= 30; x++) {
            for (int z = 20; z <= 30; z++) {
                helper.setBlock(x, 7, z, Blocks.STONE);
            }
        }
        helper.setBlock(24, 2, 24, Blocks.GLOWSTONE);
        helper.setBlock(28, 2, 22, Blocks.TORCH);
        helper.runAfterDelay(60, () -> {
            BlockPos centre = helper.absolutePos(new BlockPos(24, 3, 24));
            int[][] offsets = {{0, 0, 0}, {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            Set<Integer> skyLevels = new HashSet<>();
            boolean blockLight = false;
            int compared = 0;
            for (int[] offset : offsets) {
                int sx = SectionPos.blockToSectionCoord(centre.getX()) + offset[0];
                int sy = SectionPos.blockToSectionCoord(centre.getY()) + offset[1];
                int sz = SectionPos.blockToSectionCoord(centre.getZ()) + offset[2];
                byte[] copy = new byte[LightLevelCopy.SECTION_BLOCKS];
                LightLevelCopy.read(helper.getLevel(), sx, sy, sz, copy);
                BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            position.set(sx * 16 + x, sy * 16 + y, sz * 16 + z);
                            int sky = helper.getLevel().getBrightness(LightLayer.SKY, position);
                            int block = helper.getLevel().getBrightness(LightLayer.BLOCK, position);
                            int packed = copy[(y * 16 + z) * 16 + x] & 255;
                            helper.assertTrue(packed == LightLevelCopy.pack(sky, block),
                                    "section " + sx + " " + sy + " " + sz + " block " + position + ": copy sky "
                                            + (packed >> 4) + " block " + (packed & 15) + ", engine sky " + sky
                                            + " block " + block);
                            skyLevels.add(sky);
                            blockLight |= block > 0;
                            compared++;
                        }
                    }
                }
            }
            helper.assertTrue(compared == offsets.length * LightLevelCopy.SECTION_BLOCKS, "compared " + compared);
            helper.assertTrue(blockLight, "the glowstone and the torch must light some blocks");
            helper.assertTrue(skyLevels.size() >= 2, "under a roof the sky light differs in the sections: " + skyLevels);
            helper.succeed();
        });
    }
}
