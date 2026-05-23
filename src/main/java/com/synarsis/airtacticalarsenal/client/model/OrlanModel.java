package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class OrlanModel extends GeoModel<OrlanEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/orlan.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/orlan.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/orlan.animation.json");

    @Override
    public ResourceLocation getModelResource(OrlanEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(OrlanEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(OrlanEntity entity) {
        return ANIMATION;
    }
}
