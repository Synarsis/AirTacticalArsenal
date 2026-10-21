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
import com.synarsis.airtacticalarsenal.entity.tel.TelLamps;
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

    private static final VehicleLamps LAMPS = TelLamps.LAYOUT;
    private static final double BEAM_DOWN = 6.0;
    private static final double HALO_RANGE = 96.0;
    private static final double HIDDEN_SLACK = 0.6;

    static final int LEASE_TICKS = 2;

    interface Mount {
        Vec3 world(double[] local, float partialTick);

        Vec3 beam();

        default Vec3 beam(float partialTick) {
            return beam();
        }
    }

    private static final class Set {
        final List<AtaLights.Light> heads = new ArrayList<>();
        final List<AtaLights.Light> tails = new ArrayList<>();
        final boolean[] visible = new boolean[LAMPS.lamps().size()];
        Mount mount;
        long lastTick;
        BooleanSupplier gone;
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
        keep(id, FxScheduler.now(), tel::isRemoved, new Mount() {
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
        measure(tel);
    }

    static void keep(int id, long now, BooleanSupplier gone, Mount mount) {
        Set set = LIT.get(id);
        if (set == null) {
            set = new Set();
            BooleanSupplier stillThere = () -> !gone.getAsBoolean();
            for (VehicleLamps.Lamp lamp : LAMPS.of(VehicleLamps.Kind.HEAD)) {
                double[] local = array(lamp.local());
                set.heads.add(AtaLights.spot(mount.world(local, 1.0f))
                        .following(p -> mount.world(local, p), stillThere)
                        .colour(lamp.red(), lamp.green(), lamp.blue()).strength(3.5, 36.0)
                        .life(0, -1, 0, Fall.LINEAR).shadows(true)
                        .cone(mount.beam(1.0f), 18.0, 32.0).tintsParticles(false));
            }
            for (VehicleLamps.Lamp lamp : LAMPS.of(VehicleLamps.Kind.TAIL)) {
                double[] local = array(lamp.local());
                set.tails.add(AtaLights.point(mount.world(local, 1.0f))
                        .following(p -> mount.world(local, p), stillThere)
                        .colour(lamp.red(), lamp.green(), lamp.blue()).strength(0.5, 3.0)
                        .life(0, -1, 0, Fall.LINEAR).tintsParticles(false));
            }
            LIT.put(id, set);
        }
        set.mount = mount;
        set.lastTick = now;
        set.gone = gone;
        Vec3 axis = mount.beam(1.0f);
        for (AtaLights.Light head : set.heads) {
            head.setDirection(axis);
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

    private static void measure(IskanderTelEntity tel) {
        Set set = LIT.get(tel.getId());
        if (set == null) {
            return;
        }
        ClientLevel level = FxSpawn.level();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null || !FxView.particlesWanted()
                || FxView.distanceTo(tel.position()) > HALO_RANGE) {
            Arrays.fill(set.visible, false);
            return;
        }
        Vec3 eye = FxView.camera();
        int index = 0;
        for (VehicleLamps.Lamp lamp : LAMPS.lamps()) {
            set.visible[index++] = seen(level, mc, eye, world(tel, array(lamp.local()), 1.0f));
        }
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
        List<Draw> draws = new ArrayList<>();
        for (Set set : LIT.values()) {
            if (set.mount == null) {
                continue;
            }
            Vec3 axis = set.mount.beam(partial);
            for (AtaLights.Light head : set.heads) {
                head.setDirection(axis);
            }
            int index = 0;
            for (VehicleLamps.Lamp lamp : LAMPS.lamps()) {
                if (set.visible[index++]) {
                    draws.add(new Draw(set.mount.world(array(lamp.local()), partial), lamp));
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
            quad(buffer, rotation, eye, draw.at(), draw.lamp(), sprite);
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

    private record Draw(Vec3 at, VehicleLamps.Lamp lamp) {
    }

    private static void quad(BufferBuilder buffer, Quaternionf rotation, Vec3 eye, Vec3 at, VehicleLamps.Lamp lamp,
            TextureAtlasSprite sprite) {
        float size = lamp.size() * 0.5f;
        float x = (float) (at.x - eye.x);
        float y = (float) (at.y - eye.y);
        float z = (float) (at.z - eye.z);
        vertex(buffer, rotation, x, y, z, 1.0f, -1.0f, size, sprite.getU1(), sprite.getV1(), lamp);
        vertex(buffer, rotation, x, y, z, 1.0f, 1.0f, size, sprite.getU1(), sprite.getV0(), lamp);
        vertex(buffer, rotation, x, y, z, -1.0f, 1.0f, size, sprite.getU0(), sprite.getV0(), lamp);
        vertex(buffer, rotation, x, y, z, -1.0f, -1.0f, size, sprite.getU0(), sprite.getV1(), lamp);
    }

    private static void vertex(BufferBuilder buffer, Quaternionf rotation, float x, float y, float z, float ox,
            float oy, float size, float u, float v, VehicleLamps.Lamp lamp) {
        Vector3f offset = new Vector3f(ox, oy, 0.0f).rotate(rotation).mul(size).add(x, y, z);
        buffer.addVertex(offset.x(), offset.y(), offset.z()).setUv(u, v)
                .setColor(lamp.red(), lamp.green(), lamp.blue(), 1.0f).setLight(LightTexture.FULL_BRIGHT);
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
        double down = Math.toRadians(BEAM_DOWN);
        float yaw = Mth.rotLerp(partialTick, tel.yRotO, tel.getYRot());
        float pitch = Mth.lerp(partialTick, tel.xRotO, tel.getXRot());
        float roll = Mth.lerp(partialTick, tel.rollO, tel.roll);
        return TelGeometry.rotateToWorld(yaw, pitch, roll, new Vec3(0.0, -Math.sin(down), Math.cos(down)));
    }

    private static double[] array(Vec3 local) {
        return new double[] {local.x, local.y, local.z};
    }
}
