package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Collision layers of the physics world")
class PhysicsLayersTest {

    @Test
    @DisplayName("a vehicle collides with its own ground and other vehicles, not with another vehicle's ground")
    void rules() {
        for (int a = 0; a < PhysicsLayers.MAX_VEHICLES; a++) {
            assertTrue(PhysicsLayers.collide(PhysicsLayers.vehicle(a), PhysicsLayers.ground(a)));
            assertTrue(PhysicsLayers.collide(PhysicsLayers.ground(a), PhysicsLayers.vehicle(a)));
            assertFalse(PhysicsLayers.collide(PhysicsLayers.vehicle(a), PhysicsLayers.vehicle(a)));
            for (int b = 0; b < PhysicsLayers.MAX_VEHICLES; b++) {
                if (a != b) {
                    assertFalse(PhysicsLayers.collide(PhysicsLayers.vehicle(a), PhysicsLayers.ground(b)));
                    assertTrue(PhysicsLayers.collide(PhysicsLayers.vehicle(a), PhysicsLayers.vehicle(b)));
                }
                assertFalse(PhysicsLayers.collide(PhysicsLayers.ground(a), PhysicsLayers.ground(b)));
            }
        }
        for (int a = 0; a < PhysicsLayers.MAX_VEHICLES; a++) {
            for (int b = 0; b < PhysicsLayers.MAX_VEHICLES; b++) {
                assertEquals(a == b, PhysicsLayers.collide(PhysicsLayers.wheel(a), PhysicsLayers.ground(b)));
                assertFalse(PhysicsLayers.collide(PhysicsLayers.wheel(a), PhysicsLayers.vehicle(b)));
                assertFalse(PhysicsLayers.collide(PhysicsLayers.wheel(a), PhysicsLayers.wheel(b)));
            }
            assertFalse(PhysicsLayers.collide(PhysicsLayers.wheel(a), PhysicsLayers.DEBRIS));
        }
        assertTrue(PhysicsLayers.collide(PhysicsLayers.DEBRIS, PhysicsLayers.ground(3)));
        assertTrue(PhysicsLayers.collide(PhysicsLayers.DEBRIS, PhysicsLayers.vehicle(3)));
    }

    @Test
    @DisplayName("the native broad phase filter agrees with the rules for every layer")
    void nativeFilterAgrees() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        try (PhysicsWorld world = new PhysicsWorld(false, 1.0)) {
            for (int layer = 0; layer < PhysicsLayers.OBJECT_COUNT; layer++) {
                for (int other = 0; other < PhysicsLayers.OBJECT_COUNT; other++) {
                    if (PhysicsLayers.collide(layer, other)) {
                        assertTrue(world.broadPhaseCollides(layer, PhysicsLayers.broadPhase(other)),
                                layer + " should reach the broad phase layer of " + other);
                    }
                }
            }
            assertEquals(0, world.bodyCount());
        }
    }
}
