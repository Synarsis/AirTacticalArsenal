package com.synarsis.airtacticalarsenal.entity.tel.physics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Who may set the launcher's speed")
class TelHandoverTest {

    @Test
    @DisplayName("the client that drives ignores the server's motion packets; everyone else takes them")
    void motionPacketsAreForObserversOnly() {
        assertFalse(TelHandover.acceptsServerMotion(true));
        assertTrue(TelHandover.acceptsServerMotion(false));
    }

    @Test
    @DisplayName("when the driver gets out, the launcher keeps its ground speed and none of its height speed")
    void coastsOnAlongTheGround() {
        Vec3 coast = TelHandover.coastVelocity(new Vec3(0.4, -0.3, -0.9));
        assertEquals(0.4, coast.x, 0.0);
        assertEquals(0.0, coast.y, 0.0);
        assertEquals(-0.9, coast.z, 0.0);
    }
}
