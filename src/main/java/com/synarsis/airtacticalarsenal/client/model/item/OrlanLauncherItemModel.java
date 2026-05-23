package com.synarsis.airtacticalarsenal.client.model.item;

import com.synarsis.airtacticalarsenal.ShahedMod;
import com.synarsis.airtacticalarsenal.item.OrlanLauncherItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class OrlanLauncherItemModel extends GeoModel<OrlanLauncherItem> {

    private static final ResourceLocation MODEL = new ResourceLocation(ShahedMod.MOD_ID, "geo/orlan_launcher.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ShahedMod.MOD_ID, "textures/entity/orlan_launcher.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ShahedMod.MOD_ID, "animations/orlan.animation.json");

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
