package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class IskanderModel extends GeoModel<IskanderEntity> {
    @Override
    public ResourceLocation getModelResource(IskanderEntity entity) {
        return new ResourceLocation(ShahedMod.MOD_ID, "geo/iskander.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(IskanderEntity entity) {
        return new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/iskander.png");
    }

    @Override
    public ResourceLocation getAnimationResource(IskanderEntity entity) {
        return new ResourceLocation(ShahedMod.MOD_ID, "animations/iskander.animation.json");
    }
}
