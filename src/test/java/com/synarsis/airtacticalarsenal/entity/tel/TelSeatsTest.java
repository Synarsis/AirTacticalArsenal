package com.synarsis.airtacticalarsenal.entity.tel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Where the launcher crew sit")
class TelSeatsTest {

    private static List<double[]> cubes(double[] size, double tolerance) throws IOException {
        List<double[]> found = new ArrayList<>();
        try (InputStream in = TelSeatsTest.class.getResourceAsStream("/assets/ata/geo/iskander_tel_loaded.geo.json");
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray bones = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for (JsonElement bone : bones) {
                JsonObject b = bone.getAsJsonObject();
                if (!b.has("cubes")) {
                    continue;
                }
                for (JsonElement cube : b.getAsJsonArray("cubes")) {
                    JsonObject c = cube.getAsJsonObject();
                    JsonArray o = c.getAsJsonArray("origin");
                    JsonArray s = c.getAsJsonArray("size");
                    boolean same = true;
                    for (int i = 0; i < 3; i++) {
                        same &= Math.abs(s.get(i).getAsDouble() - size[i]) < tolerance;
                    }
                    if (same && !c.has("rotation")) {
                        found.add(new double[] {-(o.get(0).getAsDouble() + s.get(0).getAsDouble() / 2.0) / 16.0,
                                (o.get(1).getAsDouble() + s.get(1).getAsDouble() / 2.0) / 16.0,
                                (o.get(2).getAsDouble() + s.get(2).getAsDouble() / 2.0) / 16.0});
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("the steering wheel hub and the driver's seat cushion are on the same side, within a tenth of a block")
    void driverSitsUnderTheWheel() throws IOException {
        List<double[]> hubs = cubes(new double[] {1.6, 1.3, 1.6}, 0.05);
        assertEquals(1, hubs.size(), "the steering hub is the only cube of its size");
        double hub = hubs.get(0)[0];
        assertTrue(Math.abs(hub - TelGeometry.SEATS[0].x) < 0.1, "hub " + hub + " seat " + TelGeometry.SEATS[0].x);
        assertEquals(Math.signum(hub), Math.signum(TelGeometry.SEATS[0].x));
        assertEquals(Math.signum(hub), Math.signum(TelGeometry.DISMOUNTS[0].x));

        boolean cushion = false;
        for (double[] c : cubes(new double[] {11.0, 3.0, 12.0}, 0.05)) {
            cushion |= Math.abs(c[0] - TelGeometry.SEATS[0].x) < 0.1;
        }
        assertTrue(cushion, "a seat cushion lies under the driver");
    }

    @Test
    @DisplayName("the commander is in the middle, the operator on the side away from the wheel, and they get out on that side")
    void otherSeats() {
        assertEquals(0.0, TelGeometry.SEATS[1].x, 1.0E-9);
        assertEquals(-TelGeometry.SEATS[0].x, TelGeometry.SEATS[2].x, 1.0E-9);
        assertFalse(Math.signum(TelGeometry.DISMOUNTS[1].x) == Math.signum(TelGeometry.DISMOUNTS[0].x));
        assertEquals(Math.signum(TelGeometry.SEATS[2].x), Math.signum(TelGeometry.DISMOUNTS[2].x));
        assertEquals(TelGeometry.SEAT_COUNT, TelGeometry.SEATS.length);
        assertEquals(TelGeometry.SEAT_COUNT, TelGeometry.DISMOUNTS.length);
    }
}
