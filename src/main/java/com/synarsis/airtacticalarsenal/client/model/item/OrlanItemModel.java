package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.item.OrlanItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class OrlanItemModel extends GeoModel<OrlanItem> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/orlan.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/orlan.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/orlan.animation.json");

    @Override
    public ResourceLocation getModelResource(OrlanItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(OrlanItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(OrlanItem item) {
        return ANIMATION;
    }
}
