package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.client.model.OrlanModel;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class OrlanRenderer extends GeoEntityRenderer<OrlanEntity> {
    public OrlanRenderer(EntityRendererProvider.Context context) {
        super(context, new OrlanModel());
        this.shadowRadius = 1.0f;
    }

    @Override
    public void render(OrlanEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0, -1.5, 0);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void applyRotations(OrlanEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        float yaw = animatable.getYRot();
        float prevYaw = animatable.yRotO;
        float pitch = animatable.getXRot();
        float prevPitch = animatable.xRotO;

        float interpYaw = lerpAngle(prevYaw, yaw, partialTick);
        float interpPitch = prevPitch + (pitch - prevPitch) * partialTick;

        poseStack.mulPose(Axis.YP.rotationDegrees(interpYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-interpPitch));
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return from + diff * t;
    }
}
