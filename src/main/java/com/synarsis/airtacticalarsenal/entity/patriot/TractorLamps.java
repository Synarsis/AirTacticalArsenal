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
import java.util.List;

public final class TractorLamps {

    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-TractorLamps");
    private static final String GEO = "/assets/ata/geo/patriot_m983_tractor.geo.json";

    public static final VehicleLamps LAYOUT = load();

    private TractorLamps() {
    }

    private static VehicleLamps load() {
        try (InputStream stream = TractorLamps.class.getResourceAsStream(GEO)) {
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
            List<Vec3> heads = new ArrayList<>();
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                if (!bone.has("cubes") || !(name.equals("cab") || name.equals("chassis"))) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    double[] origin = triple(cube.getAsJsonArray("origin"));
                    double[] size = triple(cube.getAsJsonArray("size"));
                    double cx = origin[0] + size[0] * 0.5;
                    double cy = origin[1] + size[1] * 0.5;
                    double cz = origin[2] + size[2] * 0.5;
                    if (name.equals("cab") && size[2] <= 0.5 && size[0] >= 2.5 && size[0] <= 3.0
                            && size[1] >= 2.5 && cz > 159.0) {
                        heads.add(TelGeometry.fileToLocal(cx, cy, cz));
                    } else if (name.equals("chassis") && size[2] <= 0.6 && size[0] >= 2.5
                            && size[0] <= 3.0 && cz < -28.0) {
                        lamps.add(new VehicleLamps.Lamp(TelGeometry.fileToLocal(cx, cy, cz),
                                VehicleLamps.Kind.TAIL, 0.3f, 1.0f, 0.10f, 0.05f));
                    }
                }
            }
            if (heads.size() != 4 || lamps.size() != 2) {
                throw new IOException("expected four headlights and two rear lamps, found " + heads.size() + " and "
                        + lamps.size());
            }
            return withHeads(lamps, heads);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Could not read the lamps of the tractor from the model", error);
            return fallback();
        }
    }

    private static VehicleLamps fallback() {
        List<VehicleLamps.Lamp> lamps = new ArrayList<>();
        List<Vec3> heads = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int pair = 0; pair < 2; pair++) {
                heads.add(new Vec3(side * (1.14 + pair * 0.26), 2.64, 9.98));
            }
            lamps.add(new VehicleLamps.Lamp(new Vec3(side * 1.28, 1.0625, -1.75), VehicleLamps.Kind.TAIL, 0.3f,
                    1.0f, 0.10f, 0.05f));
        }
        return withHeads(lamps, heads);
    }

    static VehicleLamps withHeads(List<VehicleLamps.Lamp> tails, List<Vec3> heads) {
        List<VehicleLamps.Lamp> lamps = new ArrayList<>(tails);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 sum = Vec3.ZERO;
            int n = 0;
            for (Vec3 head : heads) {
                if (Math.signum(head.x) == side) {
                    sum = sum.add(head);
                    n++;
                    lamps.add(new VehicleLamps.Lamp(head, VehicleLamps.Kind.GLOW, 0.55f, 1.0f, 0.93f, 0.80f));
                }
            }
            if (n > 0) {
                lamps.add(new VehicleLamps.Lamp(sum.scale(1.0 / n), VehicleLamps.Kind.BEAM, 0.0f, 1.0f, 0.93f, 0.80f));
            }
        }
        return new VehicleLamps(lamps);
    }

    private static double[] triple(JsonArray array) {
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }
}
