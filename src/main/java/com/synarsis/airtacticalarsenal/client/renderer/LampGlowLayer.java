package com.synarsis.airtacticalarsenal.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.client.ClientTelInput;
import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import com.synarsis.airtacticalarsenal.vehicle.lamp.BrakeLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public class LampGlowLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {

    private static final Map<Entity, Motion> MOTION = new WeakHashMap<>();

    private static final class Motion {
        final BrakeLights lights = new BrakeLights();
        int tick = Integer.MIN_VALUE;
    }

    private final ResourceLocation front;
    private final ResourceLocation rear;
    private final Function<T, Entity> lampOwner;
    private final Predicate<Entity> lightsOn;
    private final Predicate<Entity> driven;

    private final Predicate<T> applies;

    public LampGlowLayer(GeoRenderer<T> renderer, String texture, boolean hasFront, Predicate<T> applies,
            Function<T, Entity> lampOwner, Predicate<Entity> lightsOn, Predicate<Entity> driven) {
        super(renderer);
        this.applies = applies;
        this.front = hasFront ? mask(texture, "front") : null;
        this.rear = mask(texture, "rear");
        this.lampOwner = lampOwner;
        this.lightsOn = lightsOn;
        this.driven = driven;
    }

    private static ResourceLocation mask(String texture, String group) {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
                "textures/entity/" + texture + "_glow_" + group + ".png");
    }

    private static BrakeLights track(Entity owner, boolean driven) {
        Motion motion = MOTION.computeIfAbsent(owner, e -> new Motion());
        if (motion.tick != owner.tickCount) {
            motion.tick = owner.tickCount;
            double yaw = Math.toRadians(owner.getYRot());
            double dx = owner.getX() - owner.xo;
            double dz = owner.getZ() - owner.zo;
            double forward = (dz * Math.cos(yaw) - dx * Math.sin(yaw)) * 20.0;
            Minecraft mc = Minecraft.getInstance();
            boolean localDriver = mc.player != null && owner.getControllingPassenger() == mc.player;
            int input = localDriver ? ClientTelInput.poll() : 0;
            motion.lights.update(forward, driven, localDriver, (input & TelInput.FWD) != 0,
                    (input & TelInput.BACK) != 0);
        }
        return motion.lights;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
            int packedOverlay) {
        Entity owner = this.applies.test(animatable) ? this.lampOwner.apply(animatable) : null;
        if (owner == null) {
            return;
        }
        boolean on = this.lightsOn.test(owner);
        BrakeLights lights = track(owner, this.driven.test(owner));
        if (this.front != null) {
            this.draw(poseStack, animatable, bakedModel, bufferSource, partialTick, this.front,
                    BrakeLights.frontBrightness(on) * (owner instanceof com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity tel
                            ? (tel.headlightMode() == 2 ? 1.0 : 0.85)
                            : owner instanceof com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity tractor
                                    ? (tractor.headlightMode() == 2 ? 1.0 : 0.85) : 1.0));
        }
        this.draw(poseStack, animatable, bakedModel, bufferSource, partialTick, this.rear, lights.rearBrightness(on));
    }

    private void draw(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource,
            float partialTick, ResourceLocation mask, double brightness) {
        if (brightness <= 0.01) {
            return;
        }
        int v = (int) Math.round(Math.min(1.0, brightness) * 255.0);
        int colour = 0xFF000000 | (v << 16) | (v << 8) | v;
        RenderType type = RenderType.eyes(mask);
        VertexConsumer consumer = bufferSource.getBuffer(type);
        this.getRenderer().reRender(model, poseStack, bufferSource, animatable, type, consumer, partialTick,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
    }
}
