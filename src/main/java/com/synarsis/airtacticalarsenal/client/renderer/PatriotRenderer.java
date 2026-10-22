package com.synarsis.airtacticalarsenal.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.synarsis.airtacticalarsenal.client.model.PatriotTrailerModel;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotGeometry;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class PatriotRenderer extends GeoEntityRenderer<PatriotVehicleEntity> {

    public PatriotRenderer(EntityRendererProvider.Context context) {
        super(context, new PatriotTrailerModel());
        this.shadowRadius = 3.0f;
        this.addRenderLayer(new LampGlowLayer<>(this, "patriot_pac2", false, e -> e.kind() == PatriotKind.LAUNCHER,
                PatriotRenderer::tractorOf, e -> ((M983TractorEntity) e).areHeadlightsOn(),
                e -> e.getControllingPassenger() != null));
        this.addRenderLayer(new LampGlowLayer<>(this, "patriot_anmpq65", false, e -> e.kind() == PatriotKind.RADAR,
                PatriotRenderer::tractorOf, e -> ((M983TractorEntity) e).areHeadlightsOn(),
                e -> e.getControllingPassenger() != null));
    }

    private static net.minecraft.world.entity.Entity tractorOf(PatriotVehicleEntity trailer) {
        return trailer.isHitched() ? trailer.coupledTractor() : null;
    }

    @Override
    protected void applyRotations(PatriotVehicleEntity animatable, PoseStack poseStack, float ageInTicks,
            float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
        float roll = animatable.getRollLerp(partialTick);

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(0.0, PatriotGeometry.PIVOT_H, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(0.0, -PatriotGeometry.PIVOT_H, 0.0);
    }

    @Override
    public RenderType getRenderType(PatriotVehicleEntity animatable, ResourceLocation texture,
            MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
