package com.synarsis.airtacticalarsenal.entity.patriot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.vehicle.lamp.VehicleLamps;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class TrailerLamps {

    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-TrailerLamps");
    private static final Map<PatriotKind, VehicleLamps> LAYOUTS = new EnumMap<>(PatriotKind.class);

    private TrailerLamps() {
    }

    public static synchronized VehicleLamps of(PatriotKind kind) {
        return LAYOUTS.computeIfAbsent(kind, TrailerLamps::load);
    }

    private static VehicleLamps load(PatriotKind kind) {
        try (InputStream stream = TrailerLamps.class.getResourceAsStream(kind.geoPath())) {
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
                if (!bone.has("cubes") || !bone.get("name").getAsString().equals("chassis")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    double[] origin = triple(cube.getAsJsonArray("origin"));
                    double[] size = triple(cube.getAsJsonArray("size"));
                    double cz = origin[2] + size[2] * 0.5;
                    if (size[2] <= 0.6 && size[0] >= 2.5 && size[0] <= 3.0 && size[1] >= 1.5 && size[1] <= 2.5
                            && cz < -90.0) {
                        lamps.add(new VehicleLamps.Lamp(TelGeometry.fileToLocal(origin[0] + size[0] * 0.5,
                                origin[1] + size[1] * 0.5, cz), VehicleLamps.Kind.TAIL, 0.3f, 1.0f, 0.10f, 0.05f));
                    }
                }
            }
            if (lamps.size() != 2) {
                throw new IOException("expected two rear lamps, found " + lamps.size());
            }
            return new VehicleLamps(lamps);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Could not read the lamps of the trailer from the model", error);
            return fallback();
        }
    }

    private static VehicleLamps fallback() {
        List<VehicleLamps.Lamp> lamps = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            lamps.add(new VehicleLamps.Lamp(new Vec3(side * 1.28, 1.06, -6.23), VehicleLamps.Kind.TAIL, 0.3f, 1.0f,
                    0.10f, 0.05f));
        }
        return new VehicleLamps(lamps);
    }

    private static double[] triple(JsonArray array) {
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }
}
