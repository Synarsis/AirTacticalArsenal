package com.synarsis.airtacticalarsenal.client.model;

import com.synarsis.airtacticalarsenal.entity.patriot.PatriotBones;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotHitbox;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotPose;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class PatriotTrailerModel extends GeoModel<PatriotVehicleEntity> {

    private static final Map<PatriotKind, List<String>> CONTROLLED = new EnumMap<>(PatriotKind.class);

    @Override
    public ResourceLocation getModelResource(PatriotVehicleEntity entity) {
        return entity.kind().geoLocation();
    }

    @Override
    public ResourceLocation getTextureResource(PatriotVehicleEntity entity) {
        return entity.kind().textureLocation();
    }

    @Override
    public ResourceLocation getAnimationResource(PatriotVehicleEntity entity) {
        return entity.kind().animationLocation();
    }

    @Override
    public void setCustomAnimations(PatriotVehicleEntity entity, long instanceId,
            AnimationState<PatriotVehicleEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        PatriotKind kind = entity.kind();
        PatriotHitbox hitbox = PatriotHitbox.of(kind);
        TelAnimSampler sampler = hitbox.sampler();
        PatriotPose pose = entity.poseFor(state.getPartialTick());
        for (String name : CONTROLLED.computeIfAbsent(kind, k -> hitbox.controlledBones())) {
            int group = PatriotBones.groupOf(kind, name);
            TelAnimSampler.BoneValue value = pose.value(kind, sampler, name, group);
            int channels = PatriotBones.channelsOf(name);
            getBone(name).ifPresent(bone -> apply(bone, value, channels));
        }
    }

    private static void apply(GeoBone bone, TelAnimSampler.BoneValue value, int channels) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        double[] turn = value == null || (channels & ModelHitbox.ROTATION) == 0 ? new double[3] : value.geckoRotation();
        double[] move = value == null || (channels & ModelHitbox.POSITION) == 0 ? new double[3] : value.geckoPosition();
        double[] scale = value == null || (channels & ModelHitbox.SCALE) == 0 ? new double[] {1.0, 1.0, 1.0}
                : value.scaleOrOne();
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
}
