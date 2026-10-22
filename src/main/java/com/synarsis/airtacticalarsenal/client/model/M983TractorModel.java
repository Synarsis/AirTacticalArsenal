package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class M983TractorModel extends GeoModel<M983TractorEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
            "geo/patriot_m983_tractor.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
            "textures/entity/patriot_m983.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
            "animations/patriot_m983_tractor.animation.json");

    @Override
    public ResourceLocation getModelResource(M983TractorEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(M983TractorEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(M983TractorEntity entity) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(M983TractorEntity entity, long instanceId, AnimationState<M983TractorEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        float partial = state.getPartialTick();
        float steer = (float) Math.toRadians(entity.getSteerNow());
        float spinLeft = entity.getSpin(partial, 0);
        float spinRight = entity.getSpin(partial, 1);
        for (int axle = 0; axle < 4; axle++) {
            setWheel("wheel_right_" + axle, spinLeft);
            setWheel("wheel_left_" + axle, spinRight);
        }
        getBone("steer_left_2").ifPresent(bone -> bone.setRotY(steer));
        getBone("steer_left_3").ifPresent(bone -> bone.setRotY(steer));
        getBone("steer_right_2").ifPresent(bone -> bone.setRotY(steer));
        getBone("steer_right_3").ifPresent(bone -> bone.setRotY(steer));
    }

    private void setWheel(String name, float spin) {
        getBone(name).ifPresent(bone -> bone.setRotX(spin));
    }
}
