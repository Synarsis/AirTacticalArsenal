package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.entity.tel.TelHeadlights;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadlightModeTest {
    @Test
    void cyclesAndFailedLamp() {
        assertEquals(1, TelHeadlights.next(0, true));
        assertEquals(2, TelHeadlights.next(1, true));
        assertEquals(0, TelHeadlights.next(2, true));
        assertEquals(0, TelHeadlights.next(2, false));
        assertEquals(0, TelHeadlights.mode(255));
    }

    @Test
    void legacyBooleanAndByteRoundTrip() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Headlights", true);
        assertEquals(1, TelHeadlights.mode(tag.getByte("Headlights")));
        tag.putBoolean("Headlights", false);
        assertEquals(0, TelHeadlights.mode(tag.getByte("Headlights")));
        for (int mode = 0; mode <= 2; mode++) {
            tag.putByte("Headlights", (byte) mode);
            assertEquals(mode, TelHeadlights.mode(tag.getByte("Headlights")));
        }
    }
}
