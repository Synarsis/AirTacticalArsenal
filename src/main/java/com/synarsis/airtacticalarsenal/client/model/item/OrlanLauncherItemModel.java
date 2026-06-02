package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.OrlanLauncherItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class OrlanLauncherItemModel extends GeoModel<OrlanLauncherItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/orlan_launcher.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "textures/entity/orlan_launcher.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "animations/orlan.animation.json");

    @Override
    public ResourceLocation getModelResource(OrlanLauncherItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(OrlanLauncherItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(OrlanLauncherItem item) {
        return ANIMATION;
    }
}
