package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class SprayCanItemModel extends GeoModel<SprayCanItem> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/spray_can.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/item/spray_can.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/spray_can.animation.json");

    @Override
    public ResourceLocation getModelResource(SprayCanItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SprayCanItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SprayCanItem item) {
        return ANIMATION;
    }
}
