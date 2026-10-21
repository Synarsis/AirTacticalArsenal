package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TelGameTests {

    private TelGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(TelPhysicsTests.class);
    }
}
