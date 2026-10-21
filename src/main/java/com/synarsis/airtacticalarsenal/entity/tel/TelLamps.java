package com.synarsis.airtacticalarsenal.entity.tel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.vehicle.lamp.VehicleLamps;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TelLamps {

    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-TelLamps");
    private static final String GEO = "/assets/ata/geo/iskander_tel_loaded.geo.json";

    public static final VehicleLamps LAYOUT = load();

    private TelLamps() {
    }

    private static VehicleLamps load() {
        try (InputStream stream = TelLamps.class.getResourceAsStream(GEO)) {
            if (stream == null) {
                throw new IOException("the model is missing from the jar");
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonArray bones = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones");
            List<VehicleLamps.Lamp> lamps = new ArrayList<>();
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    double[] origin = triple(cube.getAsJsonArray("origin"));
                    double[] size = triple(cube.getAsJsonArray("size"));
                    double cx = origin[0] + size[0] * 0.5;
                    double cy = origin[1] + size[1] * 0.5;
                    double cz = origin[2] + size[2] * 0.5;
                    if (name.equals("cab") && size[2] <= 0.5 && size[0] >= 3.0 && size[1] >= 2.5
                            && cz > 120.0) {
                        lamps.add(new VehicleLamps.Lamp(TelGeometry.fileToLocal(cx, cy, cz),
                                VehicleLamps.Kind.HEAD, 0.6f, 1.0f, 0.92f, 0.78f));
                    } else if (name.equals("chassis") && size[2] >= 1.0 && size[2] <= 2.0 && size[0] >= 6.0
                            && size[1] >= 3.0 && cz < -105.0) {
                        lamps.add(new VehicleLamps.Lamp(TelGeometry.fileToLocal(cx, cy, cz),
                                VehicleLamps.Kind.TAIL, 0.25f, 1.0f, 0.10f, 0.05f));
                    }
                }
            }
            if (lamps.size() != 4) {
                throw new IOException("expected two headlights and two rear lamps, found " + lamps.size());
            }
            return new VehicleLamps(lamps);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Could not read the lamps of the launcher from the model", error);
            return fallback();
        }
    }

    private static VehicleLamps fallback() {
        List<VehicleLamps.Lamp> lamps = new ArrayList<>();
        lamps.add(new VehicleLamps.Lamp(new Vec3(1.25, 1.6125, 7.794375), VehicleLamps.Kind.HEAD, 0.6f, 1.0f,
                0.92f, 0.78f));
        lamps.add(new VehicleLamps.Lamp(new Vec3(-1.25, 1.6125, 7.794375), VehicleLamps.Kind.HEAD, 0.6f, 1.0f,
                0.92f, 0.78f));
        lamps.add(new VehicleLamps.Lamp(new Vec3(1.3125, 1.3125, -6.925), VehicleLamps.Kind.TAIL, 0.25f, 1.0f, 0.10f,
                0.05f));
        lamps.add(new VehicleLamps.Lamp(new Vec3(-1.3125, 1.3125, -6.925), VehicleLamps.Kind.TAIL, 0.25f, 1.0f, 0.10f,
                0.05f));
        return new VehicleLamps(lamps);
    }

    private static double[] triple(JsonArray array) {
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }
}
