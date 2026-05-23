package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.item.PaintStandItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class PaintStandItemModel extends GeoModel<PaintStandItem> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/paint_stand.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/paint_stand.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/shahed.animation.json");

    @Override
    public ResourceLocation getModelResource(PaintStandItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PaintStandItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PaintStandItem item) {
        return ANIMATION;
    }
}
