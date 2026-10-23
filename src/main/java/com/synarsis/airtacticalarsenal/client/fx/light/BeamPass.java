package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.systems.RenderSystem;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BeamPass {
    private record Range(long tick, int mode, float[] values) {
    }
    private static final LightPostTarget TARGET = new LightPostTarget();
    private static final Map<Integer, Range> CACHE = new HashMap<>();
    private static boolean active;
    private static ClientLevel world;

    private BeamPass() {
    }

    static void draw(ClientLevel level, Vec3 camera, float partial, Matrix4f view, Matrix4f projection,
            int depth, int width, int height) {
        active = false;
        if (world != level) {
            CACHE.clear();
            world = level;
        }
        CameraPrecipitation weather = CameraPrecipitation.at(level, camera, partial);
        float rain = (float) weather.beamRain();
        float thunder = (float) weather.thunder();
        float fogEnd = RenderSystem.getShaderFogEnd();
        boolean snow = weather.snow();
        if (BeamMath.density(rain, thunder, fogEnd, snow) <= 0.003) return;
        List<AtaLights.Light> lights = new ArrayList<>();
        for (AtaLights.Light light : AtaLights.all()) {
            if (light.alive() && light.headlightMode() != 0 && light.position(partial).distanceToSqr(camera)
                    <= LightSelect.MAX_DISTANCE * LightSelect.MAX_DISTANCE) lights.add(light);
        }
        lights.sort(Comparator.comparingDouble(light -> light.position(partial).distanceToSqr(camera)));
        if (lights.size() > 4) lights.subList(4, lights.size()).clear();
        CACHE.keySet().retainAll(lights.stream().map(light -> light.id).toList());
        if (lights.isEmpty()) return;
        float[] ranges = new float[4 * BeamRangeMap.SAMPLES];
        for (int index = 0; index < lights.size(); index++) {
            AtaLights.Light light = lights.get(index);
            Range cached = CACHE.get(light.id);
            if (cached == null || cached.mode != light.headlightMode() || level.getGameTime() < cached.tick
                    || level.getGameTime() - cached.tick >= 2) {
                Vec3 origin = light.position(partial);
                Vec3 direction = light.direction();
                Vec3 right = light.right();
                Vec3 up = right.cross(direction);
                long clipStart = System.nanoTime();
                float[] values = BeamRangeMap.build(light.headlightMode(), light.radius(partial), (local, maximum) -> {
                    Vec3 end = origin.add(right.scale(local.x)).add(up.scale(local.y)).add(direction.scale(local.z));
                    end = origin.add(end.subtract(origin).scale(maximum));
                    return level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, Minecraft.getInstance().player)).getLocation().distanceTo(origin);
                });
                LightTimings.shared().add(LightTimings.BEAM_CLIPS, System.nanoTime() - clipStart);
                cached = new Range(level.getGameTime(), light.headlightMode(), values);
                CACHE.put(light.id, cached);
            }
            System.arraycopy(cached.values, 0, ranges, index * BeamRangeMap.SAMPLES, BeamRangeMap.SAMPLES);
        }
        EntityBoxTexture.uploadRanges(ranges);
        if (!TARGET.bind(Math.max(1, width / 4), Math.max(1, height / 4))) return;
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        LightPass.enableAdditiveBlend();
        ShaderInstance shader = LightShaders.beam();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("DepthSampler", depth);
        shader.setSampler("BoxSampler", EntityBoxTexture.texture());
        shader.safeGetUniform("InvProj").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("Weather").set(rain, thunder, fogEnd, snow ? 1.0f : 0.0f);
        for (int index = 0; index < lights.size(); index++) {
            AtaLights.Light light = lights.get(index);
            Vec3 offset = light.position(partial).subtract(camera);
            Vector3f position = view.transformPosition(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
            Vec3 axis = light.direction();
            Vector3f direction = view.transformDirection(new Vector3f((float) axis.x, (float) axis.y, (float) axis.z));
            Vec3 side = light.right();
            Vector3f right = view.transformDirection(new Vector3f((float) side.x, (float) side.y, (float) side.z));
            float radius = (float) light.radius(partial);
            float[] rect = LightBounds.beamRect(projection, position, direction, right, radius, light.headlightMode() == 2);
            if (rect == null) continue;
            shader.safeGetUniform("LightPos").set(position.x, position.y, position.z, radius);
            shader.safeGetUniform("LightDir").set(direction.x, direction.y, direction.z, 0.0f);
            shader.safeGetUniform("LightRight").set(right.x, right.y, right.z, (float) light.headlightMode());
            shader.safeGetUniform("LightColor").set(light.red(), light.green(), light.blue(), (float) light.strength(partial));
            shader.safeGetUniform("BeamParams").set(AtaClientConfig.getFxLightIntensity(), (float) index, 0.0f, 0.0f);
            LightPass.quad(rect[0], rect[1], rect[2], rect[3]);
        }
        shader.clear();
        active = true;
    }

    static void bind(ShaderInstance shader, int fallback) {
        shader.setSampler("BeamSampler", active ? TARGET.texture : fallback);
        shader.safeGetUniform("BeamSize").set(active ? (float) TARGET.width : 1.0f, active ? (float) TARGET.height : 1.0f);
        shader.safeGetUniform("BeamEnabled").set(active ? 1.0f : 0.0f);
    }
    static void reset() {active = false;}
    static void release() {
        reset();
        TARGET.release();
        CACHE.clear();
        world = null;
    }
}
