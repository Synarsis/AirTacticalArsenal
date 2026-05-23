package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.client.model.PaintStandModel;
import com.synarsis.airtacticalarsenal.entity.PaintStandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class PaintStandRenderer extends GeoEntityRenderer<PaintStandEntity> {

    public PaintStandRenderer(EntityRendererProvider.Context context) {
        super(context, new PaintStandModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(PaintStandEntity entity, float entityYaw, float partialTick, 
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();

        if (entity.hasShahed() && entity.getPaintProgress() > 0 && entity.getPaintProgress() < 1.0f) {
            if (shouldShowProgressBar(entity)) {
                renderProgressBar(entity, poseStack, bufferSource, packedLight);
            }
        }
    }

    private boolean shouldShowProgressBar(PaintStandEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        double distance = mc.player.distanceTo(entity);

        if (distance <= 5.0) {
            return true;
        }

        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof com.synarsis.airtacticalarsenal.item.SprayCanItem) {
            return distance <= 8.0;
        }

        return false;
    }

    private void renderProgressBar(PaintStandEntity entity, PoseStack poseStack, 
                                   MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(0, 2.0, 0);

        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.025F, -0.025F, 0.025F);

        float progress = entity.getPaintProgress();
        int percent = (int)(progress * 100);
        String text = percent + "%";

        Font font = Minecraft.getInstance().font;
        float x = -font.width(text) / 2.0F;

        Matrix4f matrix = poseStack.last().pose();
        int backgroundColor = (int)(Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, x, 0, 0x20FFFFFF, false, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, backgroundColor, packedLight);

        int color = getProgressColor(progress);
        font.drawInBatch(text, x, 0, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

        poseStack.popPose();
    }

    private int getProgressColor(float progress) {
        if (progress < 0.5f) {
            int g = (int)(155 + progress * 200);
            return 0xFF00FF00 | (g << 8);
        } else {
            return 0xFF00FF00;
        }
    }
}
