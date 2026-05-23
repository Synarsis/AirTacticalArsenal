package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.item.IskanderItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class IskanderItemModel extends GeoModel<IskanderItem> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/iskander.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/iskander.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/iskander.animation.json");

    @Override
    public ResourceLocation getModelResource(IskanderItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(IskanderItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(IskanderItem item) {
        return ANIMATION;
    }
}
