package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Vehicle settings file")
class VehicleSpecTest {

    @Test
    @DisplayName("the defaults describe eight wheels on four axles and the weight of the launcher")
    void defaults() {
        VehicleSpec spec = VehicleSpec.defaults();
        assertEquals(8, spec.wheelCount());
        assertEquals(42000.0, spec.body.massKg, 0.0);
        assertEquals(6, spec.gearbox.forward.length);
        assertEquals(65.0, spec.limits.forwardKmh, 0.0);
        assertEquals(20.0, spec.limits.reverseKmh, 0.0);
        assertTrue(spec.problems().isEmpty());
        assertEquals(Math.toRadians(28.0), spec.maxSteerRadians(3), 1.0e-9);
        assertEquals(Math.toRadians(17.0), spec.maxSteerRadians(2), 1.0e-9);
        assertEquals(0.0, spec.maxSteerRadians(0), 0.0);
    }

    @Test
    @DisplayName("what is written reads back the same")
    void roundTrip() {
        VehicleSpec spec = VehicleSpec.defaults();
        spec.body.massKg = 51000.0;
        spec.gearbox.forward = new double[] {6.0, 3.0, 1.0};
        VehicleSpec back = VehicleSpec.parse(spec.toJson());
        assertEquals(51000.0, back.body.massKg, 0.0);
        assertEquals(3, back.gearbox.forward.length);
        assertEquals(spec.toJson(), back.toJson());
    }

    @Test
    @DisplayName("keys that are missing keep their defaults, so a short file is enough")
    void partialFile() {
        VehicleSpec spec = VehicleSpec.parse("{\"body\": {\"massKg\": 30000}}");
        assertEquals(30000.0, spec.body.massKg, 0.0);
        assertEquals(8500.0, spec.engine.maxTorqueNm, 0.0);
        assertEquals(4, spec.wheels.axleZ.length);
    }

    @Test
    @DisplayName("a broken file or a contradictory one is refused with a reason")
    void refusals() {
        assertThrows(JsonParseException.class, () -> VehicleSpec.parse("{not json"));
        assertThrows(JsonParseException.class, () -> VehicleSpec.parse(""));
        JsonParseException error = assertThrows(JsonParseException.class,
                () -> VehicleSpec.parse("{\"wheels\": {\"steerDegrees\": [0, 10]}}"));
        assertTrue(error.getMessage().contains("steerDegrees"), error.getMessage());
        assertThrows(JsonParseException.class, () -> VehicleSpec.parse("{\"engine\": {\"minRpm\": 3000, \"maxRpm\": 2000}}"));
    }

    @Test
    @DisplayName("air drag and rolling resistance follow the formulas")
    void resistance() {
        VehicleSpec spec = VehicleSpec.defaults();
        assertEquals(0.5 * 1.2 * 8.0 * 100.0, spec.dragForce(10.0), 1.0e-9);
        assertEquals(-spec.dragForce(10.0), spec.dragForce(-10.0), 1.0e-9);
        assertEquals(0.015 * 42000.0 * 9.81, spec.rollingForce(9.81), 1.0e-6);
        assertEquals(Units.kmhToMs(65.0), spec.forwardLimitMs(), 1.0e-12);
    }

    @Test
    @DisplayName("the file is created with the defaults on the first start and read again after an edit")
    void fileLifecycle(@TempDir Path configDir) throws Exception {
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        Path file = VehicleSpecs.file(configDir);
        assertTrue(Files.isRegularFile(file));
        int first = VehicleSpecs.revision();

        Files.writeString(file, "{\"body\": {\"massKg\": 36000}}");
        assertNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(36000.0, VehicleSpecs.current().body.massKg, 0.0);
        assertTrue(VehicleSpecs.revision() > first);

        int before = VehicleSpecs.revision();
        Files.writeString(file, "{broken");
        assertNotNull(VehicleSpecs.loadFromDisk(configDir));
        assertEquals(36000.0, VehicleSpecs.current().body.massKg, 0.0, "the previous settings stay");
        assertEquals(before, VehicleSpecs.revision());

        VehicleSpecs.useDefaults();
        assertEquals(42000.0, VehicleSpecs.current().body.massKg, 0.0);
    }
}
