package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.client.model.IskanderModel;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class IskanderRenderer extends GeoEntityRenderer<IskanderEntity> {

    private static final double MODEL_CENTER_OFFSET = 4.0;

    public IskanderRenderer(EntityRendererProvider.Context context) {
        super(context, new IskanderModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    protected void applyRotations(IskanderEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick) {
        int phase = animatable.getFlightPhase();

        if (phase == IskanderEntity.PHASE_PREPARING) {
            return;
        }

        float yaw = animatable.getYRot();
        float prevYaw = animatable.yRotO;
        float pitch = animatable.getXRot();
        float prevPitch = animatable.xRotO;

        float interpYaw = lerpAngle(prevYaw, yaw, partialTick);
        float interpPitch = prevPitch + (pitch - prevPitch) * partialTick;

        poseStack.translate(0, -MODEL_CENTER_OFFSET, 0);

        poseStack.mulPose(Axis.YP.rotationDegrees(interpYaw));

        float tiltAngle = 90.0f + interpPitch;
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltAngle));
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180)
            diff -= 360;
        while (diff < -180)
            diff += 360;
        return from + diff * t;
    }
}
