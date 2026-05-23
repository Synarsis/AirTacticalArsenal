package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@OnlyIn(Dist.CLIENT)
public class IskanderRocketBlockRenderer extends GeoBlockRenderer<IskanderRocketBlockEntity> {

    public IskanderRocketBlockRenderer(BlockEntityRendererProvider.Context context) {
        super(new IskanderRocketModel());
    }

    @Override
    public void preRender(PoseStack poseStack, IskanderRocketBlockEntity animatable, 
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {

        poseStack.translate(0.015, 0.75, 0.033);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, 
                        partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(IskanderRocketBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    public static class IskanderRocketModel extends GeoModel<IskanderRocketBlockEntity> {
        private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/iskander.geo.json");
        private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/iskander.png");
        private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/iskander.animation.json");

        @Override
        public ResourceLocation getModelResource(IskanderRocketBlockEntity animatable) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(IskanderRocketBlockEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(IskanderRocketBlockEntity animatable) {
            return ANIMATION;
        }
    }
}
