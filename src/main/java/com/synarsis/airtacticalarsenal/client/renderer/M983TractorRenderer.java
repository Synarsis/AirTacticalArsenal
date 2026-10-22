package com.synarsis.airtacticalarsenal.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.synarsis.airtacticalarsenal.client.model.M983TractorModel;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.TractorGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class M983TractorRenderer extends GeoEntityRenderer<M983TractorEntity> {

    public M983TractorRenderer(EntityRendererProvider.Context context) {
        super(context, new M983TractorModel());
        this.shadowRadius = 2.5f;
        this.addRenderLayer(new LampGlowLayer<>(this, "patriot_m983", true, e -> true, e -> e,
                e -> ((M983TractorEntity) e).areHeadlightsOn(), e -> e.getControllingPassenger() != null));
    }

    @Override
    protected void applyRotations(M983TractorEntity animatable, PoseStack poseStack, float ageInTicks,
            float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
        float roll = animatable.getRollLerp(partialTick);

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(0.0, TractorGeometry.PIVOT_H, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(0.0, -TractorGeometry.PIVOT_H, 0.0);
    }

    @Override
    public RenderType getRenderType(M983TractorEntity animatable, ResourceLocation texture,
            MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
