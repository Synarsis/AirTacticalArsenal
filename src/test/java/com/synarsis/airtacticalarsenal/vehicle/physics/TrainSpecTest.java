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

@DisplayName("Trailer settings and the settings registry")
class TrainSpecTest {

    private static final String LAUNCHER = VehicleSpecs.PATRIOT_LAUNCHER_TRAILER;
    private static final String RADAR = VehicleSpecs.PATRIOT_RADAR_TRAILER;

    @AfterEach
    void reset() {
        VehicleSpecs.useDefaults();
    }

    @Test
    @DisplayName("the defaults are valid, and the launcher and radar trailers differ only in their body")
    void defaults() {
        TrainSpec launcher = TrainSpec.launcherDefaults();
        TrainSpec radar = TrainSpec.radarDefaults();
        assertTrue(launcher.problems().isEmpty(), launcher.problems().toString());
        assertTrue(radar.problems().isEmpty(), radar.problems().toString());
        assertEquals(24000.0, launcher.trailer.body.massKg, 0.0);
        assertEquals(20000.0, radar.trailer.body.massKg, 0.0);
        assertEquals(2, launcher.trailer.wheels.axleZ.length);
        assertEquals(0, countDriven(launcher.trailer));
        assertEquals(TrainSpec.SIX_DOF, launcher.coupling.type);
        assertEquals(70.0, launcher.coupling.yawLimitDegrees, 0.0);
        assertEquals(15.0, launcher.coupling.pitchLimitDegrees, 0.0);
        assertEquals(4.0, launcher.coupling.rollLimitDegrees, 0.0);
        assertEquals(60.0, launcher.coupling.trainLimitKmh, 0.0);
        assertEquals(LAUNCHER, launcher.name);
        assertEquals(RADAR, radar.name);
        assertEquals(LAUNCHER, PatriotKind.LAUNCHER.specName());
        assertEquals(RADAR, PatriotKind.RADAR.specName());
    }

    @Test
    @DisplayName("the M983 tractor settings are valid: four axles, four driven, 80 km/h forward, 15 back")
    void tractor() {
        VehicleSpec tractor = TrainSpec.m983TractorDefaults();
        assertTrue(tractor.problems().isEmpty(), tractor.problems().toString());
        assertEquals(15000.0, tractor.body.massKg, 0.0);
        assertEquals(4, tractor.wheels.axleZ.length);
        assertEquals(4, countDriven(tractor));
        assertEquals(30.0, tractor.wheels.steerDegrees[3], 0.0);
        assertEquals(21.0, tractor.wheels.steerDegrees[2], 0.0);
        assertEquals(80.0, tractor.limits.forwardKmh, 0.0);
        assertEquals(15.0, tractor.limits.reverseKmh, 0.0);
        assertEquals(VehicleSpecs.m983Tractor().name, tractor.name);
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
    @DisplayName("a file that changes one number keeps every other default of its own trailer, not the launcher's")
    void partialFileMergesOverItsOwnDefaults() {
        TrainSpec spec = TrainSpec.parse(RADAR, "{\"defaultsVersion\": " + TrainSpec.DEFAULTS_VERSION
                + ", \"coupling\": {\"yawLimitDegrees\": 60}}");
        assertEquals(60.0, spec.coupling.yawLimitDegrees, 0.0);
        assertEquals(20000.0, spec.trailer.body.massKg, 0.0);
        assertEquals(RADAR, spec.name);
        assertEquals(TrainSpec.DEFAULTS_VERSION, spec.defaultsVersion);
        assertEquals(0, TrainSpec.parse(RADAR, "{}").defaultsVersion);
    }

    @Test
    @DisplayName("nonsense is refused with the reason")
    void problems() {
        assertThrows(JsonParseException.class, () -> TrainSpec.parse(LAUNCHER, ""));
        assertThrows(JsonParseException.class, () -> TrainSpec.parse(LAUNCHER, "{broken"));
        JsonParseException error = assertThrows(JsonParseException.class, () -> TrainSpec.parse(LAUNCHER,
                "{\"coupling\": {\"type\": \"ball\"}}"));
        assertTrue(error.getMessage().contains("coupling.type"), error.getMessage());
        error = assertThrows(JsonParseException.class, () -> TrainSpec.parse(LAUNCHER,
                "{\"trailer\": {\"wheels\": {\"steerDegrees\": [0]}}}"));
        assertTrue(error.getMessage().contains("trailer."), error.getMessage());
        assertThrows(JsonParseException.class, () -> TrainSpec.parse(LAUNCHER,
                "{\"coupling\": {\"yawLimitDegrees\": 400}}"));
        error = assertThrows(JsonParseException.class, () -> TrainSpec.parse(LAUNCHER,
                "{\"coupling\": {\"trainLimitKmh\": 1}}"));
        assertTrue(error.getMessage().contains("trainLimitKmh"), error.getMessage());
    }

    @Test
    @DisplayName("every settings file is created on the first start, and the iskander file is read as before")
    void filesLifecycle(@TempDir Path configDir) throws Exception {
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        for (String name : List.of("iskander_tel", LAUNCHER, RADAR, "m983_tractor")) {
            assertTrue(Files.isRegularFile(VehicleSpecs.file(configDir, name)), name);
        }
        assertEquals(VehicleSpec.DEFAULTS_VERSION,
                VehicleSpec.parse(Files.readString(VehicleSpecs.file(configDir, "iskander_tel"))).defaultsVersion);
        assertEquals(TrainSpec.DEFAULTS_VERSION, TrainSpec.parse(LAUNCHER,
                Files.readString(VehicleSpecs.file(configDir, LAUNCHER))).defaultsVersion);

        int telRevision = VehicleSpecs.revision();
        int launcherRevision = VehicleSpecs.revision(LAUNCHER);
        int radarRevision = VehicleSpecs.revision(RADAR);
        Files.writeString(VehicleSpecs.file(configDir, LAUNCHER), "{\"defaultsVersion\": " + TrainSpec.DEFAULTS_VERSION
                + ", \"trailer\": {\"body\": {\"massKg\": 23000}}, \"coupling\": {\"trainLimitKmh\": 50}}");
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(23000.0, VehicleSpecs.train(LAUNCHER).trailer.body.massKg, 0.0);
        assertEquals(50.0, VehicleSpecs.train(LAUNCHER).coupling.trainLimitKmh, 0.0);
        assertTrue(VehicleSpecs.revision(LAUNCHER) > launcherRevision);
        assertTrue(VehicleSpecs.revision(RADAR) >= radarRevision);
        assertEquals(42000.0, VehicleSpecs.current().body.massKg, 0.0);
        assertTrue(VehicleSpecs.revision() >= telRevision);
        assertFalse(Files.exists(VehicleSpecs.file(configDir, LAUNCHER).resolveSibling(LAUNCHER + ".json.old")));
    }

    @Test
    @DisplayName("the files of the combined vehicle of stage 7 are set aside as .old and never read again")
    void stageSevenFilesGoAway(@TempDir Path configDir) throws Exception {
        Path launcher = VehicleSpecs.file(configDir, "patriot_launcher");
        Path radar = VehicleSpecs.file(configDir, "patriot_radar");
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, "{\"tractor\": {}, \"trailer\": {}}");
        Files.writeString(radar, "{\"coupling\": {\"yawLimitDegrees\": 33}}");

        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertFalse(Files.exists(launcher));
        assertFalse(Files.exists(radar));
        assertEquals("{\"tractor\": {}, \"trailer\": {}}",
                Files.readString(launcher.resolveSibling("patriot_launcher.json.old")));
        assertEquals("{\"coupling\": {\"yawLimitDegrees\": 33}}",
                Files.readString(radar.resolveSibling("patriot_radar.json.old")));
        assertEquals(70.0, VehicleSpecs.train(RADAR).coupling.yawLimitDegrees, 0.0);
        assertTrue(Files.isRegularFile(VehicleSpecs.file(configDir, LAUNCHER)));
        assertTrue(Files.isRegularFile(VehicleSpecs.file(configDir, RADAR)));
        assertNull(VehicleSpecs.loadFromDisk(configDir));
    }

    @Test
    @DisplayName("a broken trailer file is reported and left alone, the others still load")
    void brokenFile(@TempDir Path configDir) throws Exception {
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        Path file = VehicleSpecs.file(configDir, RADAR);
        Files.writeString(file, "{broken");
        String problem = VehicleSpecs.loadFromDisk(configDir);
        assertNotNull(problem);
        assertTrue(problem.contains(RADAR), problem);
        assertEquals("{broken", Files.readString(file));
        assertEquals(20000.0, VehicleSpecs.train(RADAR).trailer.body.massKg, 0.0, "the previous settings stay");
    }

    @Test
    @DisplayName("a trailer file made for older defaults is kept as .old and replaced")
    void olderDefaults(@TempDir Path configDir) throws Exception {
        Path file = VehicleSpecs.file(configDir, LAUNCHER);
        Files.createDirectories(file.getParent());
        String stale = "{\"trailer\": {\"body\": {\"massKg\": 9000}}}";
        Files.writeString(file, stale);
        Path old = file.resolveSibling(LAUNCHER + ".json.old");
        Files.writeString(old, "an even older copy");

        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(stale, Files.readString(old));
        assertEquals(24000.0, VehicleSpecs.train(LAUNCHER).trailer.body.massKg, 0.0);
        assertEquals(TrainSpec.DEFAULTS_VERSION, TrainSpec.parse(LAUNCHER, Files.readString(file)).defaultsVersion);
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(stale, Files.readString(old), "the new file is of the current version and stays");
    }

    @Test
    @DisplayName("settings sent by name reach the right entry, and an unknown name is refused")
    void byName() {
        String json = "{\"defaultsVersion\": " + TrainSpec.DEFAULTS_VERSION
                + ", \"trailer\": {\"body\": {\"massKg\": 18000}}}";
        VehicleSpecs.apply(RADAR, json);
        assertEquals(18000.0, VehicleSpecs.train(RADAR).trailer.body.massKg, 0.0);
        assertEquals(24000.0, VehicleSpecs.train(LAUNCHER).trailer.body.massKg, 0.0);
        assertThrows(IllegalArgumentException.class, () -> VehicleSpecs.apply("nobody", json));
        assertEquals(List.of("iskander_tel", LAUNCHER, RADAR, "m983_tractor"), VehicleSpecs.names());
        TrainSpec back = TrainSpec.parse(RADAR, VehicleSpecs.json(RADAR));
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
    @DisplayName("the trailer body boxes follow the bone extents of the models")
    void boxesFollowTheModel() {
        PatriotHitbox launcher = PatriotHitbox.of(PatriotKind.LAUNCHER);
        PatriotHitbox radar = PatriotHitbox.of(PatriotKind.RADAR);
        double[] pack = extents(launcher, b -> b.equals("chassis") || b.equals("traverse") || b.equals("elevation")
                || b.startsWith("canister_") || b.startsWith("lift_"));
        double[] trailer = union(TrainSpec.launcherTrailerDefaults());
        double[] shelterModel = extents(radar, b -> b.equals("chassis"));
        double[] shelter = union(TrainSpec.radarTrailerDefaults());

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
