package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The vehicle slots of a Jolt world")
class JoltSlotLimitTest {

    @Test
    @DisplayName("sixty-four vehicles fit, the sixty-fifth is refused, and closing gives the slots back")
    void limit() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")), "the native library did not load");
        PhysicsWorld world = new PhysicsWorld(false, 1.6);
        JoltVehicle[] vehicles = new JoltVehicle[PhysicsLayers.MAX_VEHICLES];
        try {
            for (int i = 0; i < vehicles.length; i++) {
                vehicles[i] = new JoltVehicle(world, VehicleSpec.defaults(), -100.5 + i * 5.0, 80.0, 100.5,
                        0.0, 0.0, 0.0, false);
            }
            assertEquals(64, PhysicsLayers.MAX_VEHICLES);
            assertEquals(PhysicsLayers.MAX_VEHICLES, world.slotsInUse());
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> new JoltVehicle(world, VehicleSpec.defaults(), 400.5, 80.0, 100.5, 0.0, 0.0, 0.0, false));
            assertEquals("too many vehicles in one world", refused.getMessage());
        } finally {
            for (JoltVehicle vehicle : vehicles) {
                if (vehicle != null) {
                    vehicle.close();
                }
            }
            assertEquals(0, world.slotsInUse());
            world.close();
        }
    }
}
