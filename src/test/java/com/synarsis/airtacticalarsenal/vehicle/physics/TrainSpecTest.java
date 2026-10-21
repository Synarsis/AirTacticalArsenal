package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.google.gson.JsonParseException;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotHitbox;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotPose;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Train settings and the settings registry")
class TrainSpecTest {

    @AfterEach
    void reset() {
        VehicleSpecs.useDefaults();
    }

    @Test
    @DisplayName("the defaults are valid, and the launcher and radar differ only in their trailer")
    void defaults() {
        TrainSpec launcher = TrainSpec.launcherDefaults();
        TrainSpec radar = TrainSpec.radarDefaults();
        assertTrue(launcher.problems().isEmpty(), launcher.problems().toString());
        assertTrue(radar.problems().isEmpty(), radar.problems().toString());
        assertEquals(15000.0, launcher.tractor.body.massKg, 0.0);
        assertEquals(24000.0, launcher.trailer.body.massKg, 0.0);
        assertEquals(20000.0, radar.trailer.body.massKg, 0.0);
        assertEquals(4, launcher.tractor.wheels.axleZ.length);
        assertEquals(2, launcher.trailer.wheels.axleZ.length);
        assertEquals(0, countDriven(launcher.trailer));
        assertEquals(4, countDriven(launcher.tractor));
        assertEquals(30.0, launcher.tractor.wheels.steerDegrees[3], 0.0);
        assertEquals(21.0, launcher.tractor.wheels.steerDegrees[2], 0.0);
        assertEquals(60.0, launcher.tractor.limits.forwardKmh, 0.0);
        assertEquals(15.0, launcher.tractor.limits.reverseKmh, 0.0);
        assertEquals(TrainSpec.SIX_DOF, launcher.coupling.type);
        assertEquals(70.0, launcher.coupling.yawLimitDegrees, 0.0);
        assertEquals(15.0, launcher.coupling.pitchLimitDegrees, 0.0);
        assertEquals(4.0, launcher.coupling.rollLimitDegrees, 0.0);
    }

    private static int countDriven(VehicleSpec spec) {
        int n = 0;
        for (boolean driven : spec.drivetrain.drivenAxle) {
            if (driven) {
                n++;
            }
        }
        return n;
    }

    @Test
    @DisplayName("a file that changes one number keeps every other Patriot default, not the launcher's")
    void partialFileMergesOverPatriotDefaults() {
        TrainSpec spec = TrainSpec.parse("patriot_radar",
                "{\"defaultsVersion\": 1, \"tractor\": {\"body\": {\"massKg\": 12000}}, \"coupling\": {\"yawLimitDegrees\": 60}}");
        assertEquals(12000.0, spec.tractor.body.massKg, 0.0);
        assertEquals(60.0, spec.coupling.yawLimitDegrees, 0.0);
        assertEquals(20000.0, spec.trailer.body.massKg, 0.0);
        assertEquals(4, spec.tractor.wheels.axleZ.length);
        assertEquals("patriot_radar", spec.name);
        assertEquals(1, spec.defaultsVersion);
        assertEquals(0, TrainSpec.parse("patriot_radar", "{}").defaultsVersion);
    }

    @Test
    @DisplayName("nonsense is refused with the reason")
    void problems() {
        assertThrows(JsonParseException.class, () -> TrainSpec.parse("patriot_launcher", ""));
        assertThrows(JsonParseException.class, () -> TrainSpec.parse("patriot_launcher", "{broken"));
        JsonParseException error = assertThrows(JsonParseException.class, () -> TrainSpec.parse("patriot_launcher",
                "{\"coupling\": {\"type\": \"ball\"}}"));
        assertTrue(error.getMessage().contains("coupling.type"), error.getMessage());
        error = assertThrows(JsonParseException.class, () -> TrainSpec.parse("patriot_launcher",
                "{\"trailer\": {\"wheels\": {\"steerDegrees\": [0]}}}"));
        assertTrue(error.getMessage().contains("trailer."), error.getMessage());
        assertThrows(JsonParseException.class, () -> TrainSpec.parse("patriot_launcher",
                "{\"coupling\": {\"yawLimitDegrees\": 400}}"));
    }

    @Test
    @DisplayName("every settings file is created on the first start, and the iskander file is read as before")
    void filesLifecycle(@TempDir Path configDir) throws Exception {
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        for (String name : List.of("iskander_tel", "patriot_launcher", "patriot_radar")) {
            assertTrue(Files.isRegularFile(VehicleSpecs.file(configDir, name)), name);
        }
        assertEquals(VehicleSpec.DEFAULTS_VERSION,
                VehicleSpec.parse(Files.readString(VehicleSpecs.file(configDir, "iskander_tel"))).defaultsVersion);
        assertEquals(TrainSpec.DEFAULTS_VERSION, TrainSpec.parse("patriot_launcher",
                Files.readString(VehicleSpecs.file(configDir, "patriot_launcher"))).defaultsVersion);

        int telRevision = VehicleSpecs.revision();
        int launcherRevision = VehicleSpecs.revision("patriot_launcher");
        int radarRevision = VehicleSpecs.revision("patriot_radar");
        Files.writeString(VehicleSpecs.file(configDir, "patriot_launcher"),
                "{\"defaultsVersion\": 1, \"tractor\": {\"body\": {\"massKg\": 14000}}}");
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(14000.0, VehicleSpecs.train("patriot_launcher").tractor.body.massKg, 0.0);
        assertEquals(24000.0, VehicleSpecs.train("patriot_launcher").trailer.body.massKg, 0.0);
        assertTrue(VehicleSpecs.revision("patriot_launcher") > launcherRevision);
        assertTrue(VehicleSpecs.revision("patriot_radar") >= radarRevision);
        assertEquals(42000.0, VehicleSpecs.current().body.massKg, 0.0);
        assertTrue(VehicleSpecs.revision() >= telRevision);
        assertFalse(Files.exists(VehicleSpecs.file(configDir, "patriot_launcher").resolveSibling("patriot_launcher.json.old")));
    }

    @Test
    @DisplayName("a broken Patriot file is reported and left alone, the others still load")
    void brokenFile(@TempDir Path configDir) throws Exception {
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        Path file = VehicleSpecs.file(configDir, "patriot_radar");
        Files.writeString(file, "{broken");
        String problem = VehicleSpecs.loadFromDisk(configDir);
        assertNotNull(problem);
        assertTrue(problem.contains("patriot_radar"), problem);
        assertEquals("{broken", Files.readString(file));
        assertEquals(20000.0, VehicleSpecs.train("patriot_radar").trailer.body.massKg, 0.0, "the previous settings stay");
    }

    @Test
    @DisplayName("a Patriot file made for older defaults is kept as .old and replaced")
    void olderDefaults(@TempDir Path configDir) throws Exception {
        Path file = VehicleSpecs.file(configDir, "patriot_launcher");
        Files.createDirectories(file.getParent());
        String stale = "{\"tractor\": {\"body\": {\"massKg\": 9000}}}";
        Files.writeString(file, stale);
        Path old = file.resolveSibling("patriot_launcher.json.old");
        Files.writeString(old, "an even older copy");

        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(stale, Files.readString(old));
        assertEquals(15000.0, VehicleSpecs.train("patriot_launcher").tractor.body.massKg, 0.0);
        assertEquals(TrainSpec.DEFAULTS_VERSION, TrainSpec.parse("patriot_launcher", Files.readString(file)).defaultsVersion);
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(stale, Files.readString(old), "the new file is of the current version and stays");
    }

    @Test
    @DisplayName("settings sent by name reach the right entry, and an unknown name is refused")
    void byName() {
        String json = "{\"defaultsVersion\": 1, \"trailer\": {\"body\": {\"massKg\": 18000}}}";
        VehicleSpecs.apply("patriot_radar", json);
        assertEquals(18000.0, VehicleSpecs.train("patriot_radar").trailer.body.massKg, 0.0);
        assertEquals(24000.0, VehicleSpecs.train("patriot_launcher").trailer.body.massKg, 0.0);
        assertThrows(IllegalArgumentException.class, () -> VehicleSpecs.apply("nobody", json));
        assertEquals(List.of("iskander_tel", "patriot_launcher", "patriot_radar"), VehicleSpecs.names());
        TrainSpec back = TrainSpec.parse("patriot_radar", VehicleSpecs.json("patriot_radar"));
        assertEquals(18000.0, back.trailer.body.massKg, 0.0);
    }

    private static double[] extents(PatriotHitbox model, Predicate<String> bones) {
        double[] e = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (ModelHitbox.LocalBox b : model.localBoxes(PatriotPose.REST)) {
            if (!bones.test(model.boneName(b.bone()))) {
                continue;
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        Vec3 p = b.centre().add(b.ax().scale(sx * b.hx())).add(b.ay().scale(sy * b.hy()))
                                .add(b.az().scale(sz * b.hz()));
                        e[0] = Math.min(e[0], p.x);
                        e[1] = Math.min(e[1], p.y);
                        e[2] = Math.min(e[2], p.z);
                        e[3] = Math.max(e[3], p.x);
                        e[4] = Math.max(e[4], p.y);
                        e[5] = Math.max(e[5], p.z);
                    }
                }
            }
        }
        return e;
    }

    private static double[] union(VehicleSpec spec) {
        double[] e = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (VehicleSpec.Box box : spec.body.boxes) {
            for (int i = 0; i < 3; i++) {
                e[i] = Math.min(e[i], box.centre[i] - box.half[i]);
                e[i + 3] = Math.max(e[i + 3], box.centre[i] + box.half[i]);
            }
        }
        return e;
    }

    @Test
    @DisplayName("the body boxes follow the bone extents of the model: the same width and length, a raised underside")
    void boxesFollowTheModel() {
        PatriotHitbox launcher = PatriotHitbox.of(PatriotKind.LAUNCHER);
        PatriotHitbox radar = PatriotHitbox.of(PatriotKind.RADAR);
        double[] tractorModel = extents(launcher, b -> b.equals("truck_chassis") || b.equals("truck_cab"));
        double[] tractor = union(TrainSpec.tractorDefaults());
        double[] pack = extents(launcher, b -> b.equals("chassis") || b.equals("traverse") || b.equals("elevation")
                || b.startsWith("canister_") || b.startsWith("lift_"));
        double[] trailer = union(TrainSpec.launcherTrailerDefaults());
        double[] shelterModel = extents(radar, b -> b.equals("chassis"));
        double[] shelter = union(TrainSpec.radarTrailerDefaults());

        assertClose(tractorModel, tractor, "tractor");
        assertClose(pack, trailer, "launcher trailer");
        assertClose(shelterModel, shelter, "radar trailer");
    }

    private static void assertClose(double[] model, double[] spec, String what) {
        for (int i = 0; i < 6; i++) {
            if (i == 1) {
                assertTrue(spec[1] >= model[1] - 0.01 && spec[1] <= model[1] + 0.5, what + " underside " + spec[1] + " of " + model[1]);
                continue;
            }
            assertEquals(model[i], spec[i], 0.15, what + " edge " + i);
        }
    }
}
