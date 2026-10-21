package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.client.model.ShahedModel;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class ShahedRenderer extends GeoEntityRenderer<ShahedEntity> {
    public ShahedRenderer(EntityRendererProvider.Context context) {
        super(context, new ShahedModel());
        this.shadowRadius = 1.5f;
    }

    @Override
    public void render(ShahedEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isOnPaintStand()) {
            this.shadowRadius = 0.0f;

            float standYaw = entity.getStandYaw();
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-standYaw));
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
            return;
        }
        this.shadowRadius = 1.5f;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void applyRotations(ShahedEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {

        if (animatable.isOnPaintStand()) {
            return;
        }

        float yaw = animatable.getYRot();
        float prevYaw = animatable.yRotO;
        float pitch = animatable.getXRot();
        float prevPitch = animatable.xRotO;

        float interpYaw = lerpAngle(prevYaw, yaw, partialTick);
        float interpPitch = lerpAngle(prevPitch, pitch, partialTick);
        float interpRoll = lerpAngle(animatable.getRollO(), animatable.getRoll(), partialTick);

        poseStack.mulPose(Axis.YP.rotationDegrees(interpYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-interpPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(interpRoll));
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return from + diff * t;
    }
}
