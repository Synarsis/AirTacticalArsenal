package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelBoneGroups;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelModelHitbox;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class IskanderTelModel extends GeoModel<IskanderTelEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
            "geo/iskander_tel_loaded.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
            "textures/entity/iskander_tel.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
            "animations/iskander_tel_loaded.animation.json");

    private static final double WHEEL_SPIN_SIGN = -1.0;
    private static final double STEER_SIGN = -1.0;

    @Override
    public ResourceLocation getModelResource(IskanderTelEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(IskanderTelEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(IskanderTelEntity entity) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(IskanderTelEntity entity, long instanceId, AnimationState<IskanderTelEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);

        setMissileHidden("round_left_missile_root", !entity.isLoaded(0));
        setMissileHidden("round_right_missile_root", !entity.isLoaded(1));

        applyProceduralBones(entity, state.getPartialTick());
        poseGroups(entity, state.getPartialTick());
    }

    private void poseGroups(IskanderTelEntity entity, float partialTick) {
        TelHitboxPose pose = entity.poseFor(partialTick);
        TelAnimSampler sampler = TelModelHitbox.get().sampler();
        for (int group = 0; group < TelBoneGroups.COUNT; group++) {
            TelHitboxPose.Part part = pose.part(group);
            for (String name : TelBoneGroups.bones(group)) {
                TelAnimSampler.BoneValue value = part.clip() == null ? null
                        : sampler.bone(part.clip(), name, part.seconds());
                getBone(name).ifPresent(bone -> apply(bone, value));
            }
        }
    }

    private static void apply(GeoBone bone, TelAnimSampler.BoneValue value) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        double[] turn = value == null ? new double[3] : value.geckoRotation();
        double[] move = value == null ? new double[3] : value.geckoPosition();
        double[] scale = value == null ? new double[] {1.0, 1.0, 1.0} : value.scaleOrOne();
        bone.setRotX(initial.getRotX() + (float) turn[0]);
        bone.setRotY(initial.getRotY() + (float) turn[1]);
        bone.setRotZ(initial.getRotZ() + (float) turn[2]);
        bone.setPosX(initial.getOffsetX() + (float) move[0]);
        bone.setPosY(initial.getOffsetY() + (float) move[1]);
        bone.setPosZ(initial.getOffsetZ() + (float) move[2]);
        bone.setScaleX((float) scale[0]);
        bone.setScaleY((float) scale[1]);
        bone.setScaleZ((float) scale[2]);
    }

    private void setMissileHidden(String bone, boolean hidden) {
        getBone(bone).ifPresent(b -> {
            b.setHidden(hidden);
            b.setChildrenHidden(hidden);
        });
    }

    private void applyProceduralBones(IskanderTelEntity entity, float partialTick) {
        double steer = Math.toRadians(entity.getSteerDegrees(partialTick)) * STEER_SIGN;
        double spinLeft = entity.getWheelRot(true, partialTick) * WHEEL_SPIN_SIGN;
        double spinRight = entity.getWheelRot(false, partialTick) * WHEEL_SPIN_SIGN;
        for (int axle = 0; axle < 4; axle++) {
            double turn = axle == 3 ? steer : (axle == 2 ? steer * 0.6 : 0.0);
            setWheel("wheel_right_" + axle, spinLeft, turn, entity.getWheelOffset(axle * 2, partialTick));
            setWheel("wheel_left_" + axle, spinRight, turn, entity.getWheelOffset(axle * 2 + 1, partialTick));
        }

        float jackY = (float) (-JACK_TRAVEL_PX * entity.getJackProgress(partialTick));
        for (String bone : new String[] {"jack_left_0", "jack_left_1", "jack_right_0", "jack_right_1"}) {
            getBone(bone).ifPresent(b -> b.setPosY(jackY));
        }
    }

    private static final double JACK_TRAVEL_PX = 9.0;

    private void setWheel(String bone, double spin, double turn, double lift) {
        getBone(bone).ifPresent(b -> {
            b.setRotX((float) spin);
            b.setRotY((float) turn);
            b.setPosY((float) (lift * 16.0));
        });
    }
}
