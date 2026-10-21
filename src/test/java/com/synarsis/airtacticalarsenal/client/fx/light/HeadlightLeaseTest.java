package com.synarsis.airtacticalarsenal.client.fx.light;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Headlights of a machine that is gone (SPEC section 15.3, revision 13)")
class HeadlightLeaseTest {

    private static final HeadlightFx.Mount MOUNT = new HeadlightFx.Mount() {
        @Override
        public Vec3 world(double[] local, float partialTick) {
            return new Vec3(local[0], local[1], local[2]);
        }

        @Override
        public Vec3 beam() {
            return new Vec3(0.0, 0.0, 1.0);
        }
    };

    @BeforeEach
    @AfterEach
    void clean() {
        HeadlightFx.clear();
        AtaLights.clear();
    }

    @Test
    @DisplayName("a machine that ticks every tick keeps its four lamps, however long it goes on")
    void alive() {
        for (long now = 100; now < 200; now++) {
            HeadlightFx.keep(7, now, () -> false, MOUNT);
            HeadlightFx.sweep(now);
            AtaLights.tick();
            assertEquals(4, AtaLights.all().size());
        }
    }

    @Test
    @DisplayName("a machine that stops ticking has none of its lights left two ticks later")
    void vanishes() {
        HeadlightFx.keep(7, 100, () -> false, MOUNT);
        HeadlightFx.sweep(100);
        AtaLights.tick();
        assertEquals(4, AtaLights.all().size());
        HeadlightFx.sweep(101);
        AtaLights.tick();
        assertEquals(4, AtaLights.all().size(), "one tick without it is not yet the end");
        HeadlightFx.sweep(102);
        AtaLights.tick();
        assertTrue(AtaLights.all().isEmpty(), "two ticks without it: " + AtaLights.all().size() + " lights still there");
        assertFalse(HeadlightFx.lit(7));
    }

    @Test
    @DisplayName("a machine that comes back is lit again as a new set, and the old one does not come back with it")
    void comesBack() {
        HeadlightFx.keep(7, 100, () -> false, MOUNT);
        HeadlightFx.sweep(102);
        AtaLights.tick();
        assertTrue(AtaLights.all().isEmpty());
        HeadlightFx.keep(9, 140, () -> false, MOUNT);
        AtaLights.tick();
        assertEquals(4, AtaLights.all().size());
    }

    @Test
    @DisplayName("a machine removed from the world puts its lights out at the next tick, without waiting out the lease")
    void removed() {
        AtomicBoolean removed = new AtomicBoolean();
        HeadlightFx.keep(7, 100, removed::get, MOUNT);
        removed.set(true);
        AtaLights.tick();
        assertTrue(AtaLights.all().isEmpty());
    }

    @Test
    @DisplayName("any light that follows something goes out when what it follows is gone")
    void followingGoes() {
        AtomicBoolean there = new AtomicBoolean(true);
        AtaLights.point(Vec3.ZERO).following(p -> new Vec3(p, 0, 0), there::get).strength(1.0, 4.0)
                .life(0, -1, 0, LightCurves.Fall.LINEAR);
        AtaLights.point(Vec3.ZERO).ownedBy(there::get).strength(1.0, 4.0).life(0, -1, 0, LightCurves.Fall.LINEAR);
        AtaLights.point(Vec3.ZERO).strength(1.0, 4.0).life(0, -1, 0, LightCurves.Fall.LINEAR);
        AtaLights.tick();
        assertEquals(3, AtaLights.all().size());
        there.set(false);
        AtaLights.tick();
        assertEquals(1, AtaLights.all().size(), "only the one that follows nothing stays");
    }
}
