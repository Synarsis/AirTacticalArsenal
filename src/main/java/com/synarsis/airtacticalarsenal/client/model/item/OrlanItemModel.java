package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.OrlanItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class OrlanItemModel extends GeoModel<OrlanItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/orlan.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/orlan.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/orlan.animation.json");

    @Override
    public ResourceLocation getModelResource(OrlanItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(OrlanItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(OrlanItem item) {
        return ANIMATION;
    }
}
