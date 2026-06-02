package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanLauncherEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class OrlanLauncherModel extends GeoModel<OrlanLauncherEntity> {

    private static final ResourceLocation MODEL_EMPTY = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/orlan_launcher.geo.json");
    private static final ResourceLocation MODEL_LOADED = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/orlan_launcher_loaded.geo.json");

    private static final ResourceLocation TEXTURE_EMPTY = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/orlan_launcher.png");
    private static final ResourceLocation TEXTURE_LOADED = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/orlan_launcher_loaded.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/orlan.animation.json");

    @Override
    public ResourceLocation getModelResource(OrlanLauncherEntity animatable) {
        return animatable.hasDrone() ? MODEL_LOADED : MODEL_EMPTY;
    }

    @Override
    public ResourceLocation getTextureResource(OrlanLauncherEntity animatable) {
        return animatable.hasDrone() ? TEXTURE_LOADED : TEXTURE_EMPTY;
    }

    @Override
    public ResourceLocation getAnimationResource(OrlanLauncherEntity animatable) {
        return ANIMATION;
    }
}
