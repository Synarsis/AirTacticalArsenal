package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.synarsis.airtacticalarsenal.client.fx.FxScheduler;
import com.synarsis.airtacticalarsenal.client.fx.FxSpawn;
import com.synarsis.airtacticalarsenal.client.fx.FxView;
import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.TrailerLamps;
import com.synarsis.airtacticalarsenal.entity.patriot.TractorLamps;
import com.synarsis.airtacticalarsenal.entity.tel.TelLamps;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.particle.FlashParticle;
import com.synarsis.airtacticalarsenal.vehicle.lamp.VehicleLamps;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@OnlyIn(Dist.CLIENT)
public final class HeadlightFx {

    private static final double HALO_RANGE = 96.0;
    private static final double HIDDEN_SLACK = 0.6;
    private static final float FADE_FLOOR = 0.01f;

    static final int LEASE_TICKS = 2;

    private static long lastRender;

    interface Mount {
        Vec3 world(double[] local, float partialTick);

        Vec3 beam();

        default Vec3 right(float partialTick) {
            return world(new double[] {-1.0, 0.0, 0.0}, partialTick)
                    .subtract(world(new double[] {0.0, 0.0, 0.0}, partialTick)).normalize();
        }

        default Vec3 beam(float partialTick) {
            return beam();
        }
    }

    private static final class Set {
        final List<AtaLights.Light> heads = new ArrayList<>();
        final List<AtaLights.Light> tails = new ArrayList<>();
        final VehicleLamps lamps;
        final boolean[] visible;
        final float[] fade;
        Mount mount;
        long lastTick;
        int mode = 1;
        BooleanSupplier gone;

        Set(VehicleLamps lamps) {
            this.lamps = lamps;
            this.visible = new boolean[lamps.lamps().size()];
            this.fade = new float[lamps.lamps().size()];
        }
    }

    private static final Map<Integer, Set> LIT = new HashMap<>();

    private HeadlightFx() {
    }

    public static void clear() {
        for (Set s : LIT.values()) {
            putOut(s);
        }
        LIT.clear();
    }

    public static void tick(IskanderTelEntity tel) {
        boolean on = !tel.isRemoved() && tel.areHeadlightsOn();
        int id = tel.getId();
        if (!on) {
            Set set = LIT.remove(id);
            if (set != null) {
                putOut(set);
            }
            return;
        }
        keep(id, FxScheduler.now(), tel::isRemoved, TelLamps.LAYOUT, new Mount() {
            @Override
            public Vec3 world(double[] local, float partialTick) {
                return HeadlightFx.world(tel, local, partialTick);
            }

            @Override
            public Vec3 beam() {
                return HeadlightFx.beam(tel, 1.0f);
            }

            @Override
            public Vec3 beam(float partialTick) {
                return HeadlightFx.beam(tel, partialTick);
            }
        });
        setMode(LIT.get(id), tel.headlightMode());
        measure(id, tel.position(), TelLamps.LAYOUT);
    }

    public static void tick(M983TractorEntity tractor) {
        boolean on = !tractor.isRemoved() && tractor.areHeadlightsOn();
        int id = tractor.getId();
        if (!on) {
            Set set = LIT.remove(id);
            if (set != null) {
                putOut(set);
            }
            return;
        }
        keep(id, FxScheduler.now(), tractor::isRemoved, TractorLamps.LAYOUT, new Mount() {
            @Override
            public Vec3 world(double[] local, float partialTick) {
                return HeadlightFx.world(tractor, local, partialTick);
            }

            @Override
            public Vec3 beam() {
                return HeadlightFx.beam(tractor, 1.0f);
            }

            @Override
            public Vec3 beam(float partialTick) {
                return HeadlightFx.beam(tractor, partialTick);
            }
        });
        setMode(LIT.get(id), tractor.headlightMode());
        measure(id, tractor.position(), TractorLamps.LAYOUT);
        PatriotVehicleEntity trailer = tractor.coupledTrailer();
        if (trailer != null && tractor.couplingState() != M983TractorEntity.CouplingState.FREE) {
            tickTrailer(trailer);
        }
    }

    private static void tickTrailer(PatriotVehicleEntity trailer) {
        int id = trailer.getId();
        VehicleLamps layout = TrailerLamps.of(trailer.kind());
        keep(id, FxScheduler.now(), trailer::isRemoved, layout, new Mount() {
            @Override
            public Vec3 world(double[] local, float partialTick) {
                return HeadlightFx.world(trailer, local, partialTick);
            }

            @Override
            public Vec3 beam() {
                return new Vec3(0.0, 0.0, -1.0);
            }
        });
        measure(id, trailer.position(), layout);
    }

    private static Vec3 world(PatriotVehicleEntity trailer, double[] local, float partialTick) {
        double x = Mth.lerp(partialTick, trailer.xo, trailer.getX());
        double y = Mth.lerp(partialTick, trailer.yo, trailer.getY());
        double z = Mth.lerp(partialTick, trailer.zo, trailer.getZ());
        float yaw = Mth.rotLerp(partialTick, trailer.yRotO, trailer.getYRot());
        float pitch = Mth.lerp(partialTick, trailer.xRotO, trailer.getXRot());
        float roll = trailer.getRollLerp(partialTick);
        return TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, new Vec3(local[0], local[1], local[2]));
    }

    static void keep(int id, long now, BooleanSupplier gone, Mount mount) {
        keep(id, now, gone, TelLamps.LAYOUT, mount);
    }

    static void keep(int id, long now, BooleanSupplier gone, VehicleLamps lamps, Mount mount) {
        Set set = LIT.get(id);
        if (set == null) {
            set = new Set(lamps);
            BooleanSupplier stillThere = () -> !gone.getAsBoolean();
            for (VehicleLamps.Lamp lamp : lamps.lamps()) {
                if (lamp.kind() != VehicleLamps.Kind.HEAD && lamp.kind() != VehicleLamps.Kind.BEAM) {
                    continue;
                }
                double[] local = array(lamp.local());
                set.heads.add(AtaLights.spot(mount.world(local, 1.0f))
                        .following(p -> mount.world(local, p), stillThere)
                        .colour(1.0f, 0.86f, 0.68f).strength(1000.0, 70.0)
                        .headlight(1, mount.right(1.0f))
                        .life(0, -1, 0, Fall.LINEAR).shadows(true).entityOwner(id)
                        .cone(mount.beam(1.0f), LightMath.HEADLIGHT_INNER_DEGREES, LightMath.HEADLIGHT_OUTER_DEGREES)
                        .tintsParticles(false));
            }
            for (VehicleLamps.Lamp lamp : lamps.of(VehicleLamps.Kind.TAIL)) {
                double[] local = array(lamp.local());
                set.tails.add(AtaLights.point(mount.world(local, 1.0f))
                        .following(p -> mount.world(local, p), stillThere)
                        .colour(lamp.red(), lamp.green(), lamp.blue()).strength(0.5, 3.0)
                        .life(0, -1, 0, Fall.LINEAR).entityOwner(id).tintsParticles(false));
            }
            LIT.put(id, set);
        }
        set.mount = mount;
        set.lastTick = now;
        set.gone = gone;
        Vec3 axis = mount.beam(1.0f);
        for (AtaLights.Light head : set.heads) {
            head.setDirection(axis);
            head.headlight(set.mode, mount.right(1.0f));
        }
    }

    private static void setMode(Set set, int mode) {
        set.mode = mode;
        for (AtaLights.Light head : set.heads) {
            head.headlight(mode, set.mount.right(1.0f)).strength(mode == 2 ? 3000.0 : 1000.0,
                    mode == 2 ? 140.0 : 70.0);
        }
    }

    public static void sweep(long now) {
        Iterator<Set> it = LIT.values().iterator();
        while (it.hasNext()) {
            Set set = it.next();
            if (set.gone.getAsBoolean() || now - set.lastTick >= LEASE_TICKS || now < set.lastTick) {
                putOut(set);
                it.remove();
            }
        }
    }

    static boolean lit(int id) {
        return LIT.containsKey(id);
    }

    private static void putOut(Set set) {
        for (AtaLights.Light l : set.heads) {
            l.kill();
        }
        for (AtaLights.Light l : set.tails) {
            l.kill();
        }
    }

    private static void measure(int id, Vec3 position, VehicleLamps lamps) {
        Set set = LIT.get(id);
        if (set == null) {
            return;
        }
        ClientLevel level = FxSpawn.level();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null || !FxView.particlesWanted()
                || FxView.distanceTo(position) > HALO_RANGE) {
            Arrays.fill(set.visible, false);
            return;
        }
        Vec3 eye = FxView.camera();
        int index = 0;
        for (VehicleLamps.Lamp lamp : lamps.lamps()) {
            set.visible[index++] = seen(level, mc, eye, world(set.mount, array(lamp.local())));
        }
    }

    private static Vec3 world(Mount mount, double[] local) {
        return mount == null ? Vec3.ZERO : mount.world(local, 1.0f);
    }

    private static boolean seen(ClientLevel level, Minecraft mc, Vec3 eye, Vec3 at) {
        HitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(at) < HIDDEN_SLACK;
    }

    public static void render(RenderLevelStageEvent event) {
        if (LIT.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = FxSpawn.level();
        if (level == null || mc.player == null || !FxView.particlesWanted()) {
            return;
        }
        TextureAtlasSprite sprite = FlashParticle.sprites == null ? null : FlashParticle.sprites.get(0, 1);
        if (sprite == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        long now = System.nanoTime();
        double frameSeconds = lastRender == 0 ? 0.0 : Math.min((now - lastRender) / 1.0e9, 0.25);
        lastRender = now;
        List<Draw> draws = new ArrayList<>();
        for (Set set : LIT.values()) {
            if (set.mount == null) {
                continue;
            }
            for (int lamp = 0; lamp < set.fade.length; lamp++) {
                set.fade[lamp] = (float) GlareMath.smoothSeen(set.fade[lamp], set.visible[lamp] ? 1.0 : 0.0,
                        frameSeconds);
            }
            Vec3 axis = set.mount.beam(partial);
            for (AtaLights.Light head : set.heads) {
                head.setDirection(axis);
                head.headlight(set.mode, set.mount.right(partial)).strength(set.mode == 2 ? 3000.0 : 1000.0,
                        set.mode == 2 ? 140.0 : 70.0);
            }
            int index = 0;
            int headIndex = 0;
            for (VehicleLamps.Lamp lamp : set.lamps.lamps()) {
                AtaLights.Light head = lamp.kind() == VehicleLamps.Kind.HEAD || lamp.kind() == VehicleLamps.Kind.BEAM
                        ? set.heads.get(headIndex++) : null;
                float fade = set.fade[index++];
                if (fade > FADE_FLOOR && lamp.kind() != VehicleLamps.Kind.BEAM) {
                    Vec3 location = set.mount.world(array(lamp.local()), partial);
                    double eyeLight = 0.0;
                    if (lamp.kind() == VehicleLamps.Kind.HEAD && head != null
                            && AtaClientConfig.getFxLighting().glare(AtaClientConfig.isFxGlareEnabled())) {
                        eyeLight = GlarePass.eye(head, eye, partial);
                    }
                    draws.add(new Draw(location, lamp, (float) GlareMath.halo(eyeLight), fade));
                }
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        Quaternionf rotation = new Quaternionf(camera.rotation());
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (Draw draw : draws) {
            quad(buffer, rotation, eye, draw.at(), draw.lamp(), sprite, draw.scale(), draw.alpha());
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private record Draw(Vec3 at, VehicleLamps.Lamp lamp, float scale, float alpha) {
    }

    private static void quad(BufferBuilder buffer, Quaternionf rotation, Vec3 eye, Vec3 at, VehicleLamps.Lamp lamp,
            TextureAtlasSprite sprite, float scale, float alpha) {
        float size = lamp.size() * 0.5f * scale;
        float x = (float) (at.x - eye.x);
        float y = (float) (at.y - eye.y);
        float z = (float) (at.z - eye.z);
        vertex(buffer, rotation, x, y, z, 1.0f, -1.0f, size, sprite.getU1(), sprite.getV1(), lamp, alpha);
        vertex(buffer, rotation, x, y, z, 1.0f, 1.0f, size, sprite.getU1(), sprite.getV0(), lamp, alpha);
        vertex(buffer, rotation, x, y, z, -1.0f, 1.0f, size, sprite.getU0(), sprite.getV0(), lamp, alpha);
        vertex(buffer, rotation, x, y, z, -1.0f, -1.0f, size, sprite.getU0(), sprite.getV1(), lamp, alpha);
    }

    private static void vertex(BufferBuilder buffer, Quaternionf rotation, float x, float y, float z, float ox,
            float oy, float size, float u, float v, VehicleLamps.Lamp lamp, float alpha) {
        Vector3f offset = new Vector3f(ox, oy, 0.0f).rotate(rotation).mul(size).add(x, y, z);
        buffer.addVertex(offset.x(), offset.y(), offset.z()).setUv(u, v)
                .setColor(lamp.red(), lamp.green(), lamp.blue(), alpha).setLight(LightTexture.FULL_BRIGHT);
    }

    private static Vec3 world(IskanderTelEntity tel, double[] local, float partialTick) {
        double x = Mth.lerp(partialTick, tel.xo, tel.getX());
        double y = Mth.lerp(partialTick, tel.yo, tel.getY());
        double z = Mth.lerp(partialTick, tel.zo, tel.getZ());
        float yaw = Mth.rotLerp(partialTick, tel.yRotO, tel.getYRot());
        float pitch = Mth.lerp(partialTick, tel.xRotO, tel.getXRot());
        float roll = Mth.lerp(partialTick, tel.rollO, tel.roll);
        return TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, new Vec3(local[0], local[1], local[2]));
    }

    private static Vec3 beam(IskanderTelEntity tel, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, tel.yRotO, tel.getYRot());
        float pitch = Mth.lerp(partialTick, tel.xRotO, tel.getXRot());
        float roll = Mth.lerp(partialTick, tel.rollO, tel.roll);
        return TelGeometry.rotateToWorld(yaw, pitch, roll, new Vec3(0.0, 0.0, 1.0));
    }

    private static Vec3 world(M983TractorEntity tractor, double[] local, float partialTick) {
        double x = Mth.lerp(partialTick, tractor.xo, tractor.getX());
        double y = Mth.lerp(partialTick, tractor.yo, tractor.getY());
        double z = Mth.lerp(partialTick, tractor.zo, tractor.getZ());
        float yaw = Mth.rotLerp(partialTick, tractor.yRotO, tractor.getYRot());
        float pitch = Mth.lerp(partialTick, tractor.xRotO, tractor.getXRot());
        float roll = tractor.getRollLerp(partialTick);
        return TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, new Vec3(local[0], local[1], local[2]));
    }

    private static Vec3 beam(M983TractorEntity tractor, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, tractor.yRotO, tractor.getYRot());
        float pitch = Mth.lerp(partialTick, tractor.xRotO, tractor.getXRot());
        float roll = tractor.getRollLerp(partialTick);
        return TelGeometry.rotateToWorld(yaw, pitch, roll, new Vec3(0.0, 0.0, 1.0));
    }

    private static double[] array(Vec3 local) {
        return new double[] {local.x, local.y, local.z};
    }
}
