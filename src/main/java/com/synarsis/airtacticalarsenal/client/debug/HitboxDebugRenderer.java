package com.synarsis.airtacticalarsenal.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.combat.hitbox.ObbProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** Wireframe of the hit geometry, toggled per player with {@code /ata debug hitbox}. */
@OnlyIn(Dist.CLIENT)
public final class HitboxDebugRenderer {

    private static boolean enabled = com.synarsis.airtacticalarsenal.config.AtaClientConfig
            .isHitboxOverlayEnabled();

    private HitboxDebugRenderer() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        com.synarsis.airtacticalarsenal.config.AtaClientConfig.setHitboxOverlay(value);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void render(RenderLevelStageEvent event) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ObbProvider provider) || provider.useAabb()) {
                continue;
            }
            for (Obb obb : provider.getRenderObbs(partialTick)) {
                drawBox(pose, lines, obb);
            }
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, Obb obb) {
        Vector3f half = obb.halfExtents();
        int rgb = obb.part().debugColor();
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        pose.pushPose();
        pose.translate(obb.center().x, obb.center().y, obb.center().z);
        pose.mulPose(obb.rotation());
        LevelRenderer.renderLineBox(pose, lines,
                -half.x, -half.y, -half.z, half.x, half.y, half.z,
                r, g, b, 1.0f);
        pose.popPose();
    }
}
