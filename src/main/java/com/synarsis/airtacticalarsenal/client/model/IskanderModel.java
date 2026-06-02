package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class IskanderModel extends GeoModel<IskanderEntity> {
    @Override
    public ResourceLocation getModelResource(IskanderEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/iskander.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(IskanderEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/iskander.png");
    }

    @Override
    public ResourceLocation getAnimationResource(IskanderEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/iskander.animation.json");
    }
}
