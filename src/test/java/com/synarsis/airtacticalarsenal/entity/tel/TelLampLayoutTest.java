package com.synarsis.airtacticalarsenal.entity.tel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.vehicle.lamp.VehicleLamps;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The lamps of the launcher sit where the model puts them")
class TelLampLayoutTest {

    private static List<Vec3> cubeCentres() throws Exception {
        try (InputStream stream = TelLampLayoutTest.class
                .getResourceAsStream("/assets/ata/geo/iskander_tel_loaded.geo.json")) {
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray bones = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones");
            List<Vec3> out = new ArrayList<>();
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonArray origin = cubeElement.getAsJsonObject().getAsJsonArray("origin");
                    JsonArray size = cubeElement.getAsJsonObject().getAsJsonArray("size");
                    out.add(TelGeometry.fileToLocal(origin.get(0).getAsDouble() + size.get(0).getAsDouble() * 0.5,
                            origin.get(1).getAsDouble() + size.get(1).getAsDouble() * 0.5,
                            origin.get(2).getAsDouble() + size.get(2).getAsDouble() * 0.5));
                }
            }
            return out;
        }
    }

    @Test
    @DisplayName("every lamp source is no farther than 0.1 blocks from the centre of its cube")
    void onTheModel() throws Exception {
        List<Vec3> centres = cubeCentres();
        for (VehicleLamps.Lamp lamp : TelLamps.LAYOUT.lamps()) {
            double nearest = Double.MAX_VALUE;
            for (Vec3 centre : centres) {
                nearest = Math.min(nearest, centre.distanceTo(lamp.local()));
            }
            assertTrue(nearest < 0.1, "lamp " + lamp.local() + " is " + nearest + " blocks from the nearest cube");
        }
    }

    @Test
    @DisplayName("a launcher has two headlights and two smaller red rear lamps")
    void countsAndColours() {
        List<VehicleLamps.Lamp> heads = TelLamps.LAYOUT.of(VehicleLamps.Kind.HEAD);
        List<VehicleLamps.Lamp> tails = TelLamps.LAYOUT.of(VehicleLamps.Kind.TAIL);
        assertEquals(2, heads.size());
        assertEquals(2, tails.size());
        for (VehicleLamps.Lamp tail : tails) {
            assertTrue(tail.red() > 0.9f && tail.green() < 0.2f && tail.blue() < 0.2f, "the rear halo must be red");
            assertTrue(tail.size() < heads.get(0).size(), "the rear halo must be smaller");
        }
    }
}
