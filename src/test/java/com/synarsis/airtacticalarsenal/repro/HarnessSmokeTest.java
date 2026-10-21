package com.synarsis.airtacticalarsenal.repro;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarnessSmokeTest {

    @Test
    void junitRuns() {
        assertEquals(4, 2 + 2);
    }

    @Test
    void minecraftValueClassesAreOnTheTestClasspath() {
        Vec3 v = new Vec3(3.0, 4.0, 0.0);
        assertEquals(5.0, v.length(), 1.0e-9);

        BlockPos p = new BlockPos(10, 64, -10);
        assertEquals(10, p.getX());
        assertEquals(64, p.getY());
        assertEquals(-10, p.getZ());
    }
}
