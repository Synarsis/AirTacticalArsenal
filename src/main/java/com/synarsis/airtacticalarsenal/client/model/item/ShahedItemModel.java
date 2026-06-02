package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.ShahedItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class ShahedItemModel extends GeoModel<ShahedItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/shahed.geo.json");
    private static final ResourceLocation TEXTURE_WHITE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/shahed.png");
    private static final ResourceLocation TEXTURE_BLACK = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/shahed_black.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/shahed.animation.json");

    private static String currentColor = "white";

    public static void setCurrentColor(String color) {
        currentColor = color;
    }

    @Override
    public ResourceLocation getModelResource(ShahedItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShahedItem item) {
        return "black".equals(currentColor) ? TEXTURE_BLACK : TEXTURE_WHITE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShahedItem item) {
        return ANIMATION;
    }
}
