package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class ShahedModel extends GeoModel<ShahedEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/shahed.geo.json");
    private static final ResourceLocation TEXTURE_WHITE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/shahed.png");
    private static final ResourceLocation TEXTURE_BLACK = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/shahed_black.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/shahed.animation.json");

    @Override
    public ResourceLocation getModelResource(ShahedEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShahedEntity entity) {

        String color = entity.getShahedColor();
        return "black".equals(color) ? TEXTURE_BLACK : TEXTURE_WHITE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShahedEntity entity) {
        return ANIMATION;
    }
}
