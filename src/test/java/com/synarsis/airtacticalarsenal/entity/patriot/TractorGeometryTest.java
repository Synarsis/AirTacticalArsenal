package com.synarsis.airtacticalarsenal.entity.patriot;

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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("M983 tractor coordinates")
class TractorGeometryTest {

    private static final String GEO = "/assets/ata/geo/patriot_m983_tractor.geo.json";

    private static JsonObject geometry() throws IOException {
        try (InputStream in = TractorGeometryTest.class.getResourceAsStream(GEO);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            assertNotNull(in, GEO);
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject();
        }
    }

    private static Map<String, double[]> pivots(JsonObject geometry) {
        Map<String, double[]> out = new HashMap<>();
        JsonArray bones = geometry.getAsJsonArray("bones");
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            JsonArray pivot = bone.getAsJsonArray("pivot");
            double[] values = new double[pivot.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = pivot.get(i).getAsDouble();
            }
            out.put(bone.get("name").getAsString(), values);
        }
        return out;
    }

    private static double[] bounds(JsonObject geometry) {
        double[] e = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            collect(element.getAsJsonObject(), e);
        }
        return e;
    }

    private static void collect(JsonObject bone, double[] e) {
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes != null) {
            for (JsonElement cubeElement : cubes) {
                JsonObject cube = cubeElement.getAsJsonObject();
                JsonArray origin = cube.getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonArray("size");
                if (size.size() != 3) {
                    continue;
                }
                for (int axis = 0; axis < 3; axis++) {
                    double low = origin.get(axis).getAsDouble();
                    double high = low + size.get(axis).getAsDouble();
                    e[axis] = Math.min(e[axis], low);
                    e[axis + 3] = Math.max(e[axis + 3], high);
                }
            }
        }
        JsonArray children = bone.getAsJsonArray("children");
        if (children != null) {
            for (JsonElement child : children) {
                collect(child.getAsJsonObject(), e);
            }
        }
    }

    @Test
    @DisplayName("the saddle is the model origin and the coupling bone sits on it")
    void saddleIsTheOrigin() throws IOException {
        Map<String, double[]> pivots = pivots(geometry());
        double[] root = pivots.get("root");
        double[] coupling = pivots.get("coupling");
        assertNotNull(root);
        assertNotNull(coupling);
        assertEquals(0.0, root[0], 1.0E-9);
        assertEquals(0.0, root[1], 1.0E-9);
        assertEquals(0.0, root[2], 1.0E-9);
        assertEquals(0.0, coupling[0], 1.0E-9);
        assertEquals(20.0, coupling[1], 1.0E-9);
        assertEquals(0.0, coupling[2], 1.0E-9);
        assertEquals(0.0, TractorGeometry.COUPLING.x, 1.0E-9);
        assertEquals(0.0, TractorGeometry.COUPLING.z, 1.0E-9);
    }

    @Test
    @DisplayName("the wheel and steering bones are where the axle table puts them")
    void axlesAndSteering() throws IOException {
        Map<String, double[]> pivots = pivots(geometry());
        double[] expectedZ = {0.0, 24.0, 116.0, 140.0};
        for (int axle = 0; axle < 4; axle++) {
            double[] left = pivots.get("wheel_left_" + axle);
            double[] right = pivots.get("wheel_right_" + axle);
            assertNotNull(left);
            assertNotNull(right);
            assertEquals(expectedZ[axle], left[2], 1.0E-9);
            assertEquals(expectedZ[axle], right[2], 1.0E-9);
            assertEquals(-left[0], right[0], 1.0E-9);
            assertEquals(TractorGeometry.WHEEL_CENTRE_Y * 16.0, left[1], 0.2);
            assertEquals(TractorGeometry.WHEEL_CENTRE_Y * 16.0, right[1], 0.2);
            if (axle >= 2) {
                assertNotNull(pivots.get("steer_left_" + axle));
                assertNotNull(pivots.get("steer_right_" + axle));
            }
        }
    }

    @Test
    @DisplayName("the driver's door and seat are on the side the geometry table says")
    void driverSide() throws IOException {
        Map<String, double[]> pivots = pivots(geometry());
        double door = -pivots.get("door_left")[0] / 16.0;
        double other = -pivots.get("door_right")[0] / 16.0;
        assertTrue(door * other < 0.0);
        assertEquals(Math.signum(door), Math.signum(TractorGeometry.SEATS[0].x));
        assertEquals(Math.signum(door), Math.signum(TractorGeometry.DISMOUNTS[0].x));
        assertEquals(Math.signum(other), Math.signum(TractorGeometry.SEATS[1].x));
        assertEquals(Math.signum(other), Math.signum(TractorGeometry.DISMOUNTS[1].x));
    }

    @Test
    @DisplayName("the model bounds are the geometry table bounds")
    void bounds() throws IOException {
        double[] raw = bounds(geometry());
        assertEquals(-TractorGeometry.HALF_WIDTH, raw[0] / 16.0, 1.0E-4);
        assertEquals(TractorGeometry.HALF_WIDTH, raw[3] / 16.0, 1.0E-4);
        assertEquals(0.0, raw[1] / 16.0, 0.1);
        assertEquals(TractorGeometry.HEIGHT, raw[4] / 16.0, 1.0E-4);
        assertEquals(TractorGeometry.REAR_Z, raw[2] / 16.0, 1.0E-4);
        assertEquals(TractorGeometry.FRONT_Z, raw[5] / 16.0, 1.0E-4);
    }

    @Test
    @DisplayName("the root hit box is a small box, not the length of the vehicle")
    void rootBox() {
        net.minecraft.world.phys.AABB box = TractorGeometry.rootBox(10.0, 64.0, 20.0);
        assertTrue(box.getXsize() <= 2.0 && box.getZsize() <= 2.0, box.toString());
        assertTrue(box.minY >= 64.0 && box.maxY <= 64.0 + TractorGeometry.HEIGHT);
    }

    @Test
    @DisplayName("part boxes stay inside the model and cover it end to end")
    void parts() {
        double from = Double.MAX_VALUE;
        double to = -Double.MAX_VALUE;
        for (int i = 0; i < TractorGeometry.PART_COUNT; i++) {
            double half = TractorGeometry.partWidth(i) * 0.5;
            assertTrue(half <= TractorGeometry.HALF_WIDTH, "part " + i + " is wider than the model");
            assertTrue(TractorGeometry.partHeight(i) <= TractorGeometry.HEIGHT + 1.0E-6);
            from = Math.min(from, TractorGeometry.PART_Z[i] - half);
            to = Math.max(to, TractorGeometry.PART_Z[i] + half);
        }
        assertTrue(from >= TractorGeometry.REAR_Z - 0.3, "parts start at " + from);
        assertTrue(to <= TractorGeometry.FRONT_Z + 0.3, "parts end at " + to);
        assertTrue(from <= TractorGeometry.REAR_Z + 0.3 && to >= TractorGeometry.FRONT_Z - 0.3);
    }

    @Test
    @DisplayName("the cab part is as tall as the model, the frame parts are low")
    void partHeights() {
        assertEquals(TractorGeometry.HEIGHT, TractorGeometry.partHeight(TractorGeometry.PART_COUNT - 1), 1.0E-6);
        assertTrue(TractorGeometry.partHeight(1) < 2.0);
    }
}
