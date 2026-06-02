package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class SprayCanItemModel extends GeoModel<SprayCanItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/spray_can.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/item/spray_can.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/spray_can.animation.json");

    @Override
    public ResourceLocation getModelResource(SprayCanItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SprayCanItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SprayCanItem item) {
        return ANIMATION;
    }
}
