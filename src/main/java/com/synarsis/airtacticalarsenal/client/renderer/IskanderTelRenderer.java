package com.synarsis.airtacticalarsenal.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.synarsis.airtacticalarsenal.client.model.IskanderTelModel;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class IskanderTelRenderer extends GeoEntityRenderer<IskanderTelEntity> {

    public IskanderTelRenderer(EntityRendererProvider.Context context) {
        super(context, new IskanderTelModel());
        this.shadowRadius = 2.5f;
    }

    @Override
    protected void applyRotations(IskanderTelEntity animatable, PoseStack poseStack, float ageInTicks,
            float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot())
                + animatable.getSwayPitch(partialTick);
        float roll = Mth.lerp(partialTick, animatable.rollO, animatable.roll)
                + animatable.getSwayRoll(partialTick);

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(0.0, TelGeometry.PIVOT_H, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(0.0, -TelGeometry.PIVOT_H, 0.0);
    }

    @Override
    public RenderType getRenderType(IskanderTelEntity animatable, ResourceLocation texture,
            MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
