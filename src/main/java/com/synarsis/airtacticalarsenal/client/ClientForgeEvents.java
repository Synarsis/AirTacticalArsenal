package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.gui.SurfaceHeightCache;
import com.synarsis.airtacticalarsenal.client.radar.RouteTerrainRenderer;
import com.synarsis.airtacticalarsenal.client.shader.NvgShaderHandler;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import com.synarsis.airtacticalarsenal.network.ClientPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "ata", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
class ClientForgeEvents {
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPacketHandler.clearAll();
        OrlanCameraHandler.stopCameraView();
        RouteTerrainRenderer.clearCache();
        SurfaceHeightCache.clear();
    }

    @SubscribeEvent
    public static void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        OrlanCameraHandler.forceStopCameraLocal();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        if (OrlanCameraHandler.handleMouseScroll(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_LEFT_ALT && event.getAction() == GLFW.GLFW_PRESS) {
            OrlanCameraHandler.handleExitKey();
        }

        if (event.getKey() == GLFW.GLFW_KEY_R) {
            OrlanCameraHandler.handleLockKey(event.getAction());
        }

        if (event.getKey() == GLFW.GLFW_KEY_N && event.getAction() == GLFW.GLFW_PRESS) {
            OrlanCameraHandler.toggleNvg();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        NvgShaderHandler.processShader();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (OrlanCameraHandler.isCameraActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!OrlanCameraHandler.isCameraActive()) return;

        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            Minecraft mc = Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            OrlanCameraOverlay.render(event.getGuiGraphics(), sw, sh);
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

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

        int charge = SprayCanItem.getCharge(sprayStack);
        int maxCharge = SprayCanItem.MAX_CHARGE;

        renderChargeIndicator(event.getGuiGraphics(), mc, charge, maxCharge);
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
