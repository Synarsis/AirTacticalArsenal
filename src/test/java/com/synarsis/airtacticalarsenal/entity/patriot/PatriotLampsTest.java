package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.vehicle.lamp.VehicleLamps;
import com.synarsis.airtacticalarsenal.vehicle.lamp.VehicleLamps.Kind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Lamps of the tractor and the trailers sit on their cubes")
class PatriotLampsTest {

    private static boolean near(List<VehicleLamps.Lamp> lamps, double x, double y, double z) {
        for (VehicleLamps.Lamp lamp : lamps) {
            if (lamp.local().distanceTo(new Vec3(x, y, z)) <= 0.1) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("the tractor has four headlamp cubes with a halo each, two beams, two red rear lamps")
    void tractor() {
        VehicleLamps layout = TractorLamps.LAYOUT;
        List<VehicleLamps.Lamp> glow = layout.of(Kind.GLOW);
        assertEquals(4, glow.size());
        for (double x : new double[] {-1.4, -1.1375, 1.1375, 1.4}) {
            boolean found = false;
            for (VehicleLamps.Lamp lamp : glow) {
                found |= Math.abs(lamp.local().x - x) < 0.1 && Math.abs(lamp.local().y - 2.64) < 0.1
                        && Math.abs(lamp.local().z - 9.99) < 0.1;
            }
            assertTrue(found, "no headlamp near x " + x);
        }
        List<VehicleLamps.Lamp> beams = layout.of(Kind.BEAM);
        assertEquals(2, beams.size());
        assertTrue(beams.get(0).local().x * beams.get(1).local().x < 0.0, "one beam per side");
        for (VehicleLamps.Lamp beam : beams) {
            assertEquals(0.0f, beam.size(), 0.0f, "a beam has no halo of its own");
            double sum = 0.0;
            int n = 0;
            for (VehicleLamps.Lamp lamp : glow) {
                if (Math.signum(lamp.local().x) == Math.signum(beam.local().x)) {
                    sum += lamp.local().x;
                    n++;
                }
            }
            assertEquals(sum / n, beam.local().x, 1.0e-9, "the beam sits between the two cubes of its side");
        }
        List<VehicleLamps.Lamp> tails = layout.of(Kind.TAIL);
        assertEquals(2, tails.size());
        for (VehicleLamps.Lamp tail : tails) {
            assertTrue(Math.abs(tail.local().y - 1.06) < 0.1 && Math.abs(tail.local().z + 1.76) < 0.1,
                    "rear lamp at " + tail.local());
        }
    }

    @Test
    @DisplayName("each trailer has two red rear lamps at the tail of the model")
    void trailers() {
        for (PatriotKind kind : PatriotKind.values()) {
            List<VehicleLamps.Lamp> tails = TrailerLamps.of(kind).of(Kind.TAIL);
            assertEquals(2, tails.size(), kind.toString());
            assertTrue(near(tails, 1.2, 1.06, -6.23) || near(tails, -1.2, 1.06, -6.23)
                    || near(tails, 1.3, 1.06, -6.23) || near(tails, -1.3, 1.06, -6.23), kind + " " + tails);
            for (VehicleLamps.Lamp tail : tails) {
                assertTrue(Math.abs(tail.local().y - 1.06) < 0.1 && Math.abs(tail.local().z + 6.23) < 0.1,
                        kind + " lamp at " + tail.local());
            }
            assertTrue(tails.get(0).local().x * tails.get(1).local().x < 0.0, kind + " one lamp per side");
        }
    }
}
