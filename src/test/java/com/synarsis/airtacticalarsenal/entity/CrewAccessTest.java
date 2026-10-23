package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.TractorGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Who may open the panel and switch the headlights")
class CrewAccessTest {

    private static final int TEL_CREW = 2;

    @Test
    @DisplayName("the driver and the passenger of the launcher and of the tractor may")
    void crewMay() {
        assertTrue(CrewAccess.mayOperate(true, 0, TEL_CREW));
        assertTrue(CrewAccess.mayOperate(true, 1, TEL_CREW));
        assertTrue(CrewAccess.mayOperate(true, 0, TractorGeometry.SEAT_COUNT));
        assertTrue(CrewAccess.mayOperate(true, 1, TractorGeometry.SEAT_COUNT));
    }

    @Test
    @DisplayName("somebody who is not aboard, or sits in the third seat, may not")
    void othersMayNot() {
        assertFalse(CrewAccess.mayOperate(false, 0, TEL_CREW));
        assertFalse(CrewAccess.mayOperate(false, 1, TractorGeometry.SEAT_COUNT));
        assertFalse(CrewAccess.mayOperate(true, 2, TEL_CREW));
        assertFalse(CrewAccess.mayOperate(true, 2, TractorGeometry.SEAT_COUNT));
        assertFalse(CrewAccess.mayOperate(true, -1, TEL_CREW));
    }

    @Test
    @DisplayName("the tractor seats lie farther from the entity origin than the panel range, so distance cannot decide")
    void seatsAreFarFromTheOrigin() {
        for (Vec3 seat : TractorGeometry.SEATS) {
            assertTrue(Math.hypot(seat.x, seat.z) > M983TractorEntity.PANEL_RANGE, seat.toString());
        }
        assertTrue(TelGeometry.SEATS.length >= TEL_CREW);
    }
}
