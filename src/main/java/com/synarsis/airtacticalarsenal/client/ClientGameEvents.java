package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.client.gui.SurfaceHeightCache;
import com.synarsis.airtacticalarsenal.client.radar.RouteTerrainRenderer;
import com.synarsis.airtacticalarsenal.client.shader.NvgShaderHandler;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import com.synarsis.airtacticalarsenal.network.ClientPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientGameEvents {

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPacketHandler.clearAll();
        TelClientEffects.clear();
        OrlanCameraHandler.stopCameraView();
        RouteTerrainRenderer.clearCache();
        SurfaceHeightCache.clear();
    }

    @SubscribeEvent
    public static void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        OrlanCameraHandler.forceStopCameraLocal();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        OrlanCameraHandler.tick();
        OrlanCameraHandler.tickLockOn();
        OrlanCameraHandler.tickAltitude();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (OrlanCameraHandler.isCameraActive()) {
            OrlanCameraHandler.updateZoomSmooth((float) event.getPartialTick());
            event.setFOV(event.getFOV() / OrlanCameraHandler.getZoomLevel());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!OrlanCameraHandler.isCameraActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (OrlanCameraHandler.updateTrackingAngles((float) event.getPartialTick())) {
            mc.player.setXRot(OrlanCameraHandler.getCameraPitch());
            mc.player.setYRot(OrlanCameraHandler.getCameraYaw());
        } else {
            OrlanCameraHandler.setCameraYaw(mc.player.getYRot());
            OrlanCameraHandler.setCameraPitch(mc.player.getXRot());
            mc.player.setXRot(OrlanCameraHandler.getCameraPitch());
        }

        event.setYaw(OrlanCameraHandler.getCameraYaw());
        event.setPitch(OrlanCameraHandler.getCameraPitch());
        event.setRoll(0);
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (OrlanCameraHandler.handleMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS
                && ModKeys.SETTINGS.matches(event.getKey(), event.getScanCode())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new com.synarsis.airtacticalarsenal.client.gui.AtaSettingsScreen(null));
            }
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS
                && ModKeys.CAMERA_EXIT.matches(event.getKey(), event.getScanCode())) {
            OrlanCameraHandler.handleExitKey();
        }
        if (ModKeys.CAMERA_LOCK.matches(event.getKey(), event.getScanCode())) {
            OrlanCameraHandler.handleLockKey(event.getAction());
        }
        if (event.getAction() == GLFW.GLFW_PRESS
                && ModKeys.NIGHT_VISION.matches(event.getKey(), event.getScanCode())) {
            OrlanCameraHandler.toggleNvg();
        }
        if (event.getAction() == GLFW.GLFW_PRESS
                && ModKeys.TEL_FIRE_CONTROL.matches(event.getKey(), event.getScanCode())) {
            openTelFireControl();
        }
        if (event.getAction() == GLFW.GLFW_PRESS
                && ModKeys.TEL_HEADLIGHTS.matches(event.getKey(), event.getScanCode())) {
            toggleTelHeadlights();
        }
    }

    private static void toggleTelHeadlights() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (mc.player.getVehicle() instanceof com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity tel
                && tel.isCrewSeat(mc.player)) {
            com.synarsis.airtacticalarsenal.network.NetworkHandler.sendToServer(
                    new com.synarsis.airtacticalarsenal.network.TelCommandPacket(tel.getId(),
                            com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity.CMD_HEADLIGHTS, (byte) 0, 0));
        }
    }

    private static void openTelFireControl() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (mc.player.getVehicle() instanceof com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity tel
                && tel.isCrewSeat(mc.player)) {
            mc.setScreen(new com.synarsis.airtacticalarsenal.client.gui.TelFireControlScreen(tel));
        } else if (mc.player.getVehicle() instanceof com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity patriot
                && patriot.isCrewSeat(mc.player)) {
            mc.setScreen(new com.synarsis.airtacticalarsenal.client.gui.PatriotControlScreen(patriot));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        NvgShaderHandler.processShader();
        com.synarsis.airtacticalarsenal.client.debug.HitboxDebugRenderer.render(event);
        com.synarsis.airtacticalarsenal.client.debug.PhysicsDebugRenderer.render(event);
        com.synarsis.airtacticalarsenal.client.fx.light.LightDebug.render(event);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (OrlanCameraHandler.isCameraActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!OrlanCameraHandler.isCameraActive()) return;

        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            Minecraft mc = Minecraft.getInstance();
            OrlanCameraOverlay.render(event.getGuiGraphics(),
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPostGuiLayer(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            com.synarsis.airtacticalarsenal.client.hud.HitFeedback.render(event.getGuiGraphics());
        }
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        ItemStack sprayStack = null;
        if (mainHand.getItem() instanceof SprayCanItem) {
            sprayStack = mainHand;
        } else if (offHand.getItem() instanceof SprayCanItem) {
            sprayStack = offHand;
        }
        if (sprayStack == null) return;

        renderChargeIndicator(event.getGuiGraphics(), mc,
                SprayCanItem.getCharge(sprayStack), SprayCanItem.MAX_CHARGE);
    }

    private static void renderChargeIndicator(GuiGraphics graphics, Minecraft mc, int charge, int maxCharge) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = 50;
        int barHeight = 6;

        int x = screenWidth / 2 + 95;
        int y = screenHeight - 25;

        float ratio = (float) charge / maxCharge;
        int filledWidth = (int) (barWidth * ratio);

        int r = (int) ((1.0f - ratio) * 255);
        int g = (int) (ratio * 255);
        int color = 0xFF000000 | (r << 16) | (g << 8);

        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0x80000000);
        graphics.fill(x, y, x + filledWidth, y + barHeight, color);
        graphics.renderOutline(x - 1, y - 1, barWidth + 2, barHeight + 2, 0xFF333333);

        int percent = (int) (ratio * 100);
        String text = percent + "%";
        graphics.drawString(mc.font, text, x + barWidth + 5, y - 1, 0xFFFFFF, true);
    }
}
