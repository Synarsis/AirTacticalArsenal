package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.ShahedLauncherEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ShahedLauncherModel extends GeoModel<ShahedLauncherEntity> {

    private static final ResourceLocation MODEL_EMPTY = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/shahed_launcher.geo.json");

    private static final ResourceLocation MODEL_LOADED = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/shahed_launcher_loaded.geo.json");

    private static final ResourceLocation MODEL_LOADED_BLACK = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/shahed_launcher_loaded_black.geo.json");

    private static final ResourceLocation TEXTURE_EMPTY = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/shahed_launcher.png");

    private static final ResourceLocation TEXTURE_LOADED = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/shahed_launcher_loaded.png");

    private static final ResourceLocation TEXTURE_LOADED_BLACK = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/shahed_launcher_loaded_black.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/shahed.animation.json");

    @Override
    public ResourceLocation getModelResource(ShahedLauncherEntity animatable) {
        if (!animatable.hasDrone()) return MODEL_EMPTY;
        return "black".equals(animatable.getShahedColor()) ? MODEL_LOADED_BLACK : MODEL_LOADED;
    }

    @Override
    public ResourceLocation getTextureResource(ShahedLauncherEntity animatable) {
        if (!animatable.hasDrone()) return TEXTURE_EMPTY;
        return "black".equals(animatable.getShahedColor()) ? TEXTURE_LOADED_BLACK : TEXTURE_LOADED;
    }

    @Override
    public ResourceLocation getAnimationResource(ShahedLauncherEntity animatable) {
        return ANIMATION;
    }
}
