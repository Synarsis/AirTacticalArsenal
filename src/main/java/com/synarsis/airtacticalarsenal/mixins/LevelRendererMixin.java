package com.synarsis.airtacticalarsenal.mixins;

import com.synarsis.airtacticalarsenal.client.OrlanCameraHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Orlan FPV (part 2). {@link LevelRenderer#setupRender} positions the render-section grid (ViewArea)
 * using the PLAYER BODY's position ({@code minecraft.player.getX/Y/Z}), and only re-positions it when
 * the body's chunk section changes. Since the body stays on the ground during FPV, the render grid
 * never follows the drone, so the drone's (cached) chunks have no render sections and show as empty
 * sky. This redirects those three position reads to the camera entity (the drone) while FPV is active,
 * so the render grid tracks the drone. The body itself is not moved (stays killable on the ground).
 *
 * Works together with {@code ClientChunkCacheMixin} (which keeps the chunk cache centred on the drone).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double ata$renderCenterX(LocalPlayer player) {
        Entity cam = ata$fpvCamera();
        return cam != null ? cam.getX() : player.getX();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double ata$renderCenterY(LocalPlayer player) {
        Entity cam = ata$fpvCamera();
        return cam != null ? cam.getY() : player.getY();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double ata$renderCenterZ(LocalPlayer player) {
        Entity cam = ata$fpvCamera();
        return cam != null ? cam.getZ() : player.getZ();
    }

    @Unique
    private static Entity ata$fpvCamera() {
        if (!OrlanCameraHandler.isCameraActive()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        return (cam != null && cam != mc.player) ? cam : null;
    }
}
