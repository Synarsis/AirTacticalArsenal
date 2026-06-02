package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class OrlanModel extends GeoModel<OrlanEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/orlan.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/orlan.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/orlan.animation.json");

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
