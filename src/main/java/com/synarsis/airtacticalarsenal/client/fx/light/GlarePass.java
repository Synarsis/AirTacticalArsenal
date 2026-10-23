package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GlarePass {
    private record Eye(AtaLights.Light light, Vec3 position, double strength) {
    }
    private static final int[][] TARGETS = new int[2][];
    private static final List<Eye> EYES = new ArrayList<>();
    private static final Map<Integer, Integer> SLOTS = new HashMap<>();
    private static int current;
    private static long lastFrame;
    private static boolean valid;

    private GlarePass() {
    }

    static double eye(AtaLights.Light light, Vec3 camera, float partial) {
        Vec3 offset = camera.subtract(light.position(partial));
        Vec3 direction = light.direction();
        Vec3 right = light.right();
        Vec3 up = right.cross(direction);
        double pattern = BeamPattern.pattern(light.headlightMode(), offset.dot(right), offset.dot(up), offset.dot(direction));
        return GlareMath.eye(light.strength(partial) * AtaClientConfig.getFxLightIntensity(), pattern, offset.lengthSqr(), 1.0);
    }

    static void draw(Matrix4f view, Matrix4f projection, Vec3 camera, float partial, int depth, int width, int height) {
        EYES.clear();
        for (AtaLights.Light light : AtaLights.all()) {
            if (light.headlightMode() == 0) continue;
            double strength = eye(light, camera, partial);
            Vec3 offset = light.position(partial).subtract(camera);
            Vector3f position = view.transformPosition(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
            if (strength > 0.000001 && position.z < -0.05f) EYES.add(new Eye(light,
                    new Vec3(position.x, position.y, position.z), strength));
        }
        EYES.sort(Comparator.comparingDouble(Eye::strength).reversed());
        if (EYES.size() > 4) EYES.subList(4, EYES.size()).clear();
        if (EYES.isEmpty()) {
            lastFrame = 0;
            valid = false;
            SLOTS.clear();
            return;
        }
        for (int index = 0; index < 2; index++) {
            if (TARGETS[index] == null) TARGETS[index] = LightPass.createColorTarget(5, 1, true);
            if (TARGETS[index] == null) {
                reset();
                return;
            }
        }
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0.0f : (float) Math.min((now - lastFrame) / 1.0e9, 0.25);
        lastFrame = now;
        int next = 1 - current;
        ShaderInstance shader = LightShaders.glare();
        RenderSystem.disableBlend();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, TARGETS[next][0]);
        RenderSystem.viewport(0, 0, 5, 1);
        RenderSystem.setShader(() -> shader);
        shader.setSampler("DepthSampler", depth);
        shader.setSampler("PreviousSampler", TARGETS[current][1]);
        shader.safeGetUniform("Proj").set(projection);
        shader.safeGetUniform("DepthSize").set((float) width, (float) height);
        shader.safeGetUniform("InvProj").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("Timing").set(elapsed, valid ? 1.0f : 0.0f, (float) GlareMath.LAMP_RADIUS, 0.0f);
        float[] before = new float[4];
        for (int index = 0; index < 4; index++) {
            before[index] = index < EYES.size() ? GlareMath.previousSlot(SLOTS, EYES.get(index).light.id) : -1.0f;
        }
        shader.safeGetUniform("Slots").set(before[0], before[1], before[2], before[3]);
        SLOTS.clear();
        for (int index = 0; index < EYES.size(); index++) {
            SLOTS.put(EYES.get(index).light.id, index);
        }
        for (int index = 0; index < 4; index++) {
            Eye eye = index < EYES.size() ? EYES.get(index) : null;
            shader.safeGetUniform("Eye" + index).set(eye == null ? 0.0f : (float) eye.position.x,
                    eye == null ? 0.0f : (float) eye.position.y, eye == null ? 0.0f : (float) eye.position.z,
                    eye == null ? 0.0f : (float) eye.strength);
        }
        LightPass.quad(-1, -1, 1, 1);
        shader.clear();
        current = next;
        valid = true;
    }

    static void bind(ShaderInstance shader, int fallback) {
        shader.setSampler("GlareSampler", valid ? TARGETS[current][1] : fallback);
        shader.safeGetUniform("GlareCount").set(valid ? (float) EYES.size() : 0.0f);
        for (int index = 0; index < 4; index++) {
            Eye eye = valid && index < EYES.size() ? EYES.get(index) : null;
            Vec3 direction = eye == null ? Vec3.ZERO : eye.position.normalize();
            shader.safeGetUniform("GlareDir" + index).set((float) direction.x, (float) direction.y, (float) direction.z);
        }
    }

    static void reset() {
        valid = false;
        lastFrame = 0;
        EYES.clear();
        SLOTS.clear();
    }

    static void release() {
        reset();
        for (int index = 0; index < 2; index++) {
            if (TARGETS[index] != null) {
                GL30.glDeleteFramebuffers(TARGETS[index][0]);
                GlStateManager._deleteTexture(TARGETS[index][1]);
                TARGETS[index] = null;
            }
        }
    }
}
