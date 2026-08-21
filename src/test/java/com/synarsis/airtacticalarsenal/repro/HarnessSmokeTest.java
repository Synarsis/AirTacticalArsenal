package com.synarsis.airtacticalarsenal.repro;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke test for the adversarial-review harness itself.
 *
 * Proves two things before any repro test is trusted:
 *  1. JUnit 5 runs at all under the ModDevGradle build.
 *  2. Plain Minecraft value classes (Vec3, BlockPos) are on the test classpath and
 *     usable WITHOUT Bootstrap.bootStrap() — which is what makes pure-java repro
 *     tests of the mod's trajectory math possible outside a running game.
 */
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
