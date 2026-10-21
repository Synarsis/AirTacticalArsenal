package com.synarsis.airtacticalarsenal.client.renderer.item;

import com.synarsis.airtacticalarsenal.client.model.item.ShahedItemModel;
import com.synarsis.airtacticalarsenal.item.ShahedItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class ShahedItemRenderer extends GeoItemRenderer<ShahedItem> {

    public ShahedItemRenderer() {
        super(new ShahedItemModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        String color = "white";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            String stored = data.copyTag().getString("ShahedColor");
            if (!stored.isEmpty()) {
                color = stored;
            }
        }
        ShahedItemModel.setCurrentColor(color);

        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    }
}
