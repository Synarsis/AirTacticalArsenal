package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.IskanderItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class IskanderItemModel extends GeoModel<IskanderItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/iskander.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/iskander.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/iskander.animation.json");

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
