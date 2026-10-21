package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import com.synarsis.airtacticalarsenal.client.fx.light.HazeSources;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class FxClientEvents {

    private FxClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null) {
            return;
        }
        com.synarsis.airtacticalarsenal.client.fx.light.HeadlightFx.sweep(FxScheduler.now());
        FxScheduler.tick();
        AtaLights.tick();
        HazeSources.tick();
        FxScreenFlash.tick();
        FxCameraShake.tick();
        if (FxScheduler.now() % 20 == 0) {
            TrailFx.prune();
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        FxCameraShake.apply(event);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        FxScheduler.clear();
        AtaLights.clear();
        HazeSources.clear();
        FxScreenFlash.clear();
        FxCameraShake.clear();
        FxBudget.reset();
        FxView.reset();
        com.synarsis.airtacticalarsenal.client.fx.light.LightPass.release();
        LaunchFx.clear();
        com.synarsis.airtacticalarsenal.client.fx.light.HeadlightFx.clear();
        TrailFx.clear();
    }
}
