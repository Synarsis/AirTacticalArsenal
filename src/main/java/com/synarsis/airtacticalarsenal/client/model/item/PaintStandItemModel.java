package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.PaintStandItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class PaintStandItemModel extends GeoModel<PaintStandItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/paint_stand.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/paint_stand.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/shahed.animation.json");

    @Override
    public ResourceLocation getModelResource(PaintStandItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PaintStandItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PaintStandItem item) {
        return ANIMATION;
    }
}
