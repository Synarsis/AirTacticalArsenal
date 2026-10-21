package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class FxScreenFlash implements LayeredDraw.Layer {

    private static double strength;
    private static int ticksLeft;

    private static final double LINE_SLACK = 2.5;

    public static void flash(Vec3 point, double power) {
        Minecraft mc = Minecraft.getInstance();
        if (!AtaClientConfig.isFxScreenFlashEnabled() || mc.level == null || mc.gameRenderer == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 from = camera.getPosition();
        double distance = from.distanceTo(point);
        Vec3 toPoint = distance < 1.0E-6 ? Vec3.ZERO : point.subtract(from).scale(1.0 / distance);
        Vector3f look = camera.getLookVector();
        double cos = look.x * toPoint.x + look.y * toPoint.y + look.z * toPoint.z;

        boolean clear = true;
        if (mc.player != null) {
            HitResult hit = mc.level.clip(new ClipContext(from, point, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mc.player));
            clear = hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(point) < LINE_SLACK;
        }
        double s = FxCurves.flashStrength(power, clear, cos, distance);
        if (s < 0.02) {
            return;
        }
        strength = Math.max(Math.min(1.0, s), ticksLeft > 0 ? strength * ticksLeft / FxCurves.FLASH_TICKS : 0.0);
        ticksLeft = FxCurves.FLASH_TICKS;
    }

    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    public static void clear() {
        ticksLeft = 0;
        strength = 0.0;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        if (ticksLeft <= 0 || !AtaClientConfig.isFxScreenFlashEnabled()) {
            return;
        }
        float age = FxCurves.FLASH_TICKS - ticksLeft + delta.getGameTimeDeltaPartialTick(false);
        float t = Mth.clamp(age / FxCurves.FLASH_TICKS, 0.0f, 1.0f);
        int alpha = (int) (255.0 * strength * (1.0 - t) * 0.9);
        int green = (int) Mth.lerp(t, 255.0f, 160.0f);
        int blue = (int) Mth.lerp(t, 255.0f, 70.0f);
        if (alpha <= 0) {
            return;
        }
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | (255 << 16) | (green << 8) | blue);
    }
}
