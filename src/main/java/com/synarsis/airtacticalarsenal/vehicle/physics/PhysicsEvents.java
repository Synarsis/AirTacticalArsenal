package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID)
public final class PhysicsEvents {

    private PhysicsEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        PhysicsWorlds.tick(event.getLevel());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
            PhysicsWorlds.unload(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PhysicsWorlds.unloadAll(false);
    }
}
