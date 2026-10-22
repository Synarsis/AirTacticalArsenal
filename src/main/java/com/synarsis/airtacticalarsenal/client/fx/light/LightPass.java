package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.config.FxLighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LightPass {

    private static final long IDLE_RELEASE_NANOS = 10_000_000_000L;
    private static final float BYTE_RANGE = 0.25f;
    private static final int SHADOW_LIGHTS = 4;

    private static TextureTarget depthCopy;
    private static TextureTarget sceneCopy;
    private static int lightFbo;
    private static int lightTex;
    private static int hazeFbo;
    private static int hazeTex;
    private static int lightWidth;
    private static int lightHeight;
    private static int hazeWidth;
    private static int hazeHeight;
    private static int copyWidth;
    private static int copyHeight;
    private static boolean floatBuffers = true;
    private static long lastUsed;
    private static long lastShadowUsed;

    private static volatile int lastLights;
    private static volatile int lastHaze;
    private static volatile int lastShadowLights;
    private static volatile double lastMillis;
    private static volatile boolean lastGpuTimed;
    private static int timerQuery;
    private static boolean timerPending;
    private static int boxCount;

    private LightPass() {
    }

    public static int lastLights() {
        return lastLights;
    }

    public static int lastBoxes() {
        return boxCount;
    }

    public static int lastShadowLights() {
        return lastShadowLights;
    }

    public static int lastHazePatches() {
        return lastHaze;
    }

    public static double lastMillis() {
        return lastMillis;
    }

    public static boolean gpuTimed() {
        return lastGpuTimed;
    }

    public static boolean floatBuffers() {
        return floatBuffers;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        OccupancyVolume.beginFrame();
        try {
            run(event);
        } catch (RuntimeException e) {
            disabledBy = e;
            AirTacticalArsenal.LOGGER.error("The lighting pass failed and is switched off until the game is restarted", e);
        }
    }

    private static RuntimeException disabledBy;

    public static void release() {
        if (depthCopy != null) {
            depthCopy.destroyBuffers();
            depthCopy = null;
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        releaseLightBuffers();
        releaseHazeBuffer();
        OccupancyVolume.release();
        EntityBoxTexture.release();
        boxCount = 0;
        copyWidth = 0;
        copyHeight = 0;
        if (timerQuery != 0) {
            GL15.glDeleteQueries(timerQuery);
            timerQuery = 0;
            timerPending = false;
        }
    }

    private static void releaseLightBuffers() {
        if (lightFbo != 0) {
            GL30.glDeleteFramebuffers(lightFbo);
            lightFbo = 0;
        }
        if (lightTex != 0) {
            GlStateManager._deleteTexture(lightTex);
            lightTex = 0;
        }
        lightWidth = 0;
        lightHeight = 0;
    }

    private static void releaseHazeBuffer() {
        if (hazeFbo != 0) {
            GL30.glDeleteFramebuffers(hazeFbo);
            hazeFbo = 0;
        }
        if (hazeTex != 0) {
            GlStateManager._deleteTexture(hazeTex);
            hazeTex = 0;
        }
        hazeWidth = 0;
        hazeHeight = 0;
    }

    private static void run(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        FxLighting mode = mode();
        if (disabledBy != null || mode == FxLighting.OFF || mc.level == null || !LightShaders.ready()
                || ShaderPackGuard.shaderPackInUse()) {
            lastLights = 0;
            lastHaze = 0;
            lastShadowLights = 0;
            idle(now);
            return;
        }
        ClientLevel level = mc.level;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        var frustum = event.getFrustum();

        List<LightSelect.Candidate> picked = LightSelect.pick(AtaLights.candidates(partial), cam.x, cam.y, cam.z,
                (x, y, z, r) -> frustum.isVisible(new AABB(x - r, y - r, z - r, x + r, y + r, z + r)), mode.maxLights());
        List<HazeSources.Source> hazes = visibleHaze(mode, partial, cam, frustum);
        lastLights = picked.size();
        lastHaze = hazes.size();
        if (picked.isEmpty() && hazes.isEmpty()) {
            lastShadowLights = 0;
            idle(now);
            return;
        }
        lastUsed = now;
        double started = System.nanoTime();
        boolean timed = LightDebug.enabled();
        if (timed) {
            beginTimer();
        }

        RenderTarget main = mc.getMainRenderTarget();
        boolean wantHaze = !hazes.isEmpty();

        State saved = saveState();
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            if (!ensureTargets(main, mode, wantHaze)) {
                abandonTimer(timed);
                return;
            }
            wantHaze = wantHaze && hazeFbo != 0 && LightShaders.haze() != null;

            Set<Integer> blockShadowed = blockShadowed(picked, mode);
            boolean volumeWanted = !blockShadowed.isEmpty();
            lastShadowLights = blockShadowed.size();
            if (volumeWanted) {
                lastShadowUsed = now;
                OccupancyVolume.update(level, cam, shadowFocus(blockShadowed, partial));
            } else if (OccupancyVolume.active() && now - lastShadowUsed > IDLE_RELEASE_NANOS && !LightDebug.voxels()) {
                OccupancyVolume.release();
            }
            boolean volumeReady = volumeWanted && OccupancyVolume.active();
            boxCount = 0;
            if (volumeReady) {
                OccupancyGrid grid = OccupancyVolume.grid();
                List<EntityShadowBoxes.Source> sources = new ArrayList<>();
                for (LightSelect.Candidate c : picked) {
                    AtaLights.Light light = blockShadowed.contains(c.id()) ? AtaLights.byId(c.id()) : null;
                    if (light != null) {
                        sources.add(new EntityShadowBoxes.Source(light.position(partial), c.radius()));
                    }
                }
                List<EntityShadowMath.Box> boxes = EntityShadowBoxes.collect(level, mc, cam, partial, sources,
                        grid.originX(), grid.originY(), grid.originZ());
                boxCount = boxes.isEmpty() ? 0 : EntityBoxTexture.upload(boxes);
            }

            Matrix4f view = new Matrix4f(event.getModelViewMatrix());
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());

            GlStateManager._disableScissorTest();

            depthCopy.copyDepthFrom(main);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(true, true, true, true);

            clearLightBuffer();
            if (!picked.isEmpty()) {
                enableAdditiveBlend();
                drawLights(picked, mode, view, projection, main, cam, partial, volumeReady ? blockShadowed : Set.of());
            }
            if (wantHaze) {
                enableAdditiveBlend();
                drawHaze(hazes, view, projection, cam, partial, main);
            }
            composite(main, level, cam, partial, wantHaze);
        } finally {
            restoreState(saved);
            RenderSystem.setShader(() -> previousShader);
        }
        if (timed) {
            endTimer(started);
        } else {
            lastMillis = (System.nanoTime() - started) / 1.0E6;
            lastGpuTimed = false;
        }
    }

    private static FxLighting mode() {
        try {
            return AtaClientConfig.getFxLighting();
        } catch (IllegalStateException e) {
            return FxLighting.OFF;
        }
    }

    private static void idle(long now) {
        if (depthCopy != null && lastUsed != 0 && now - lastUsed > IDLE_RELEASE_NANOS) {
            release();
        }
    }

    private static boolean ensureTargets(RenderTarget main, FxLighting mode, boolean wantHaze) {
        int w = main.width;
        int h = main.height;
        if (depthCopy == null) {
            depthCopy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            sceneCopy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copyWidth = w;
            copyHeight = h;
        } else if (copyWidth != w || copyHeight != h) {
            depthCopy.resize(w, h, Minecraft.ON_OSX);
            sceneCopy.resize(w, h, Minecraft.ON_OSX);
            copyWidth = w;
            copyHeight = h;
        }
        if (main.isStencilEnabled() && !depthCopy.isStencilEnabled()) {
            depthCopy.enableStencil();
        }
        int lw = mode.halfResolution() ? Math.max(1, w / 2) : w;
        int lh = mode.halfResolution() ? Math.max(1, h / 2) : h;
        if (lightFbo == 0 || lightWidth != lw || lightHeight != lh) {
            releaseLightBuffers();
            int[] made = createColorTarget(lw, lh, true);
            if (made == null) {
                floatBuffers = false;
                made = createColorTarget(lw, lh, false);
            } else {
                floatBuffers = true;
            }
            if (made == null) {
                disabledBy = new IllegalStateException("no usable light buffer on this GL context");
                AirTacticalArsenal.LOGGER.error("No render target could be made for the lighting pass; it is off");
                return false;
            }
            lightFbo = made[0];
            lightTex = made[1];
            lightWidth = lw;
            lightHeight = lh;
        }
        if (wantHaze && floatBuffers) {
            int hw = Math.max(1, w / 2);
            int hh = Math.max(1, h / 2);
            if (hazeFbo == 0 || hazeWidth != hw || hazeHeight != hh) {
                releaseHazeBuffer();
                int[] made = createColorTarget(hw, hh, true);
                if (made != null) {
                    hazeFbo = made[0];
                    hazeTex = made[1];
                    hazeWidth = hw;
                    hazeHeight = hh;
                }
            }
        }
        return true;
    }

    private static int[] createColorTarget(int w, int h, boolean floating) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int tex = GL11.glGenTextures();
        GlStateManager._bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        TexUpload.Store store = TexUpload.begin();
        try {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, floating ? GL30.GL_RGBA16F : GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, floating ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } finally {
            TexUpload.end(store);
        }
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        int error = GL11.glGetError();
        GlStateManager._bindTexture(previousTexture);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE || error != GL11.GL_NO_ERROR) {
            GL30.glDeleteFramebuffers(fbo);
            GlStateManager._deleteTexture(tex);
            return null;
        }
        return new int[] {fbo, tex};
    }

    private static void clearLightBuffer() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, lightFbo);
        RenderSystem.viewport(0, 0, lightWidth, lightHeight);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void enableAdditiveBlend() {
        RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }

    private static void disableBlendForComposite() {
        RenderSystem.enableBlend();
        RenderSystem.disableBlend();
    }

    private static Set<Integer> blockShadowed(List<LightSelect.Candidate> picked, FxLighting mode) {
        Set<Integer> out = new HashSet<>();
        for (LightSelect.Candidate c : picked) {
            if (out.size() >= mode.blockShadowLights()) {
                break;
            }
            AtaLights.Light light = AtaLights.byId(c.id());
            if (light != null && light.shadows()) {
                out.add(c.id());
            }
        }
        return out;
    }

    private static List<Vec3> shadowFocus(Set<Integer> ids, float partial) {
        List<Vec3> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            AtaLights.Light light = AtaLights.byId(id);
            if (light != null) {
                out.add(light.position(partial));
            }
        }
        return out;
    }

    private static void drawLights(List<LightSelect.Candidate> picked, FxLighting mode, Matrix4f view, Matrix4f projection,
            RenderTarget main, Vec3 cam, float partial, Set<Integer> blockShadowed) {
        ShaderInstance shader = LightShaders.volume();

        Matrix4f inverse = new Matrix4f(projection).invert();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        boolean volume = !blockShadowed.isEmpty() && OccupancyVolume.active();
        shader.setSampler("OccupancySampler", volume ? OccupancyVolume.texture() : depthCopy.getDepthTextureId());
        boolean boxes = volume && boxCount > 0 && EntityBoxTexture.texture() != 0;
        shader.setSampler("BoxSampler", boxes ? EntityBoxTexture.texture() : depthCopy.getDepthTextureId());
        shader.safeGetUniform("Scatter").set((float) LightMath.FILL, (float) LightMath.SPILL,
                (float) Math.cos(Math.toRadians(LightMath.SPILL_DEGREES)), (float) LightMath.FILL_REACH);
        if (volume) {
            OccupancyGrid grid = OccupancyVolume.grid();
            shader.safeGetUniform("InvView").set(new Matrix4f(view).invert());
            shader.safeGetUniform("OccOrigin").set((float) (grid.originX() - cam.x), (float) (grid.originY() - cam.y),
                    (float) (grid.originZ() - cam.z), 0.0f);
            shader.safeGetUniform("OccMod").set((float) Math.floorMod(grid.originX(), OccupancyGrid.SIZE_X),
                    (float) Math.floorMod(grid.originY(), OccupancyGrid.SIZE_Y),
                    (float) Math.floorMod(grid.originZ(), OccupancyGrid.SIZE_Z), 0.0f);
        }
        shader.safeGetUniform("InvProj").set(inverse);
        shader.safeGetUniform("Proj").set(projection);
        shader.safeGetUniform("DepthSize").set((float) main.width, (float) main.height);
        shader.safeGetUniform("FogRange").set(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd());
        shader.safeGetUniform("AccScale").set(floatBuffers ? 1.0f : BYTE_RANGE);

        int shadowsLeft = mode.shadows() ? SHADOW_LIGHTS : 0;
        for (LightSelect.Candidate c : picked) {
            AtaLights.Light light = AtaLights.byId(c.id());
            if (light == null) {
                continue;
            }
            Vec3 p = light.position(partial);
            Vector3f world = new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
            Vector3f v = new Vector3f(world);
            view.transformPosition(v);
            float radius = (float) c.radius();
            float[] rect = LightBounds.screenRect(projection, v, radius);
            if (rect == null) {
                continue;
            }
            Vec3 axis = light.direction();
            Vector3f dir = new Vector3f((float) axis.x, (float) axis.y, (float) axis.z);
            view.transformDirection(dir);
            boolean spot = light.kind() == AtaLights.Kind.SPOT;
            boolean shadow = shadowsLeft > 0 && light.shadows();
            if (shadow) {
                shadowsLeft--;
            }
            float strength = (float) c.intensity();
            shader.safeGetUniform("LightPos").set(v.x, v.y, v.z, radius);
            shader.safeGetUniform("LightColor").set(light.red() * strength, light.green() * strength,
                    light.blue() * strength, (float) light.wrap());
            shader.safeGetUniform("LightDir").set(dir.x, dir.y, dir.z, (float) light.cosOuter());
            shader.safeGetUniform("LightCone").set((float) light.cosInner(), spot ? 1.0f : 0.0f, shadow ? 1.0f : 0.0f,
                    mode.normals() ? 1.0f : 0.0f);
            boolean blocks = volume && blockShadowed.contains(c.id());
            shader.safeGetUniform("LightWorld").set(world.x, world.y, world.z, radius);
            shader.safeGetUniform("Shadow").set(blocks ? 1.0f : 0.0f, (float) mode.shadowRays(),
                    (float) light.sourceRadius(), (float) VoxelShadowMath.freeNearLight(spot, radius));
            shader.safeGetUniform("Boxes").set(blocks && boxes ? (float) boxCount : 0.0f, (float) light.entityOwner(),
                    (float) EntityShadowMath.FREE_NEAR_OWN_LIGHT, (float) EntityShadowMath.MARGIN);
            quad(rect[0], rect[1], rect[2], rect[3]);
        }
        shader.clear();
    }

    private static List<HazeSources.Source> visibleHaze(FxLighting mode, float partial, Vec3 cam,
            net.minecraft.client.renderer.culling.Frustum frustum) {
        List<HazeSources.Source> out = new ArrayList<>();
        if (!mode.haze() || !hazeSwitchedOn() || !floatBuffers) {
            return out;
        }
        for (HazeSources.Source s : HazeSources.all()) {
            if (s.strength(partial) <= 0.01) {
                continue;
            }
            Vec3 p = s.position(partial);
            double r = s.radius();
            if (p.distanceToSqr(cam) > LightSelect.MAX_DISTANCE * LightSelect.MAX_DISTANCE) {
                continue;
            }
            if (frustum.isVisible(new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r))) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean hazeSwitchedOn() {
        try {
            return AtaClientConfig.isFxHeatHazeEnabled();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static void drawHaze(List<HazeSources.Source> hazes, Matrix4f view, Matrix4f projection, Vec3 cam, float partial,
            RenderTarget main) {
        ShaderInstance shader = LightShaders.haze();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hazeFbo);
        RenderSystem.viewport(0, 0, hazeWidth, hazeHeight);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        RenderSystem.setShader(() -> shader);
        shader.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        shader.safeGetUniform("InvProj").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("HazeSize").set((float) hazeWidth, (float) hazeHeight);
        float pixels = 6.0f * main.height / 1080.0f;
        shader.safeGetUniform("MaxShift").set(pixels / main.width, pixels / main.height);
        float seconds = (System.nanoTime() / 1_000_000L % 100_000_000L) / 1000.0f;
        for (HazeSources.Source s : hazes) {
            Vec3 p = s.position(partial);
            Vector3f v = new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
            view.transformPosition(v);
            float[] rect = LightBounds.screenRect(projection, v, (float) s.radius());
            if (rect == null) {
                continue;
            }
            shader.safeGetUniform("Rect").set((rect[0] + rect[2]) * 0.5f, (rect[1] + rect[3]) * 0.5f,
                    (rect[2] - rect[0]) * 0.5f, (rect[3] - rect[1]) * 0.5f);
            shader.safeGetUniform("Haze").set(Math.max(0.0f, -v.z), (float) s.strength(partial), seconds, 0.0f);
            quad(rect[0], rect[1], rect[2], rect[3]);
        }
        shader.clear();
    }

    private static void composite(RenderTarget main, ClientLevel level, Vec3 cam, float partial, boolean haze) {
        ShaderInstance shader = LightShaders.composite();
        main.bindWrite(true);
        disableBlendForComposite();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        double day = dayFactor(level, cam, partial);
        float intensity = 1.0f;
        try {
            intensity = AtaClientConfig.getFxLightIntensity();
        } catch (IllegalStateException ignored) {
        }
        float scale = (float) (day * intensity / (floatBuffers ? 1.0f : BYTE_RANGE));

        RenderSystem.setShader(() -> shader);
        shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
        shader.setSampler("LightSampler", lightTex);
        shader.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        shader.setSampler("HazeSampler", haze ? hazeTex : lightTex);
        shader.safeGetUniform("Params").set(scale, haze ? 1.0f : 0.0f, LightDebug.bufferView() ? 1.0f : 0.0f,
                floatBuffers ? 1.0f : 1.0f / BYTE_RANGE);
        quad(-1.0f, -1.0f, 1.0f, 1.0f);
        shader.clear();
    }

    static double dayFactor(ClientLevel level, Vec3 cam, float partial) {
        double sky = LightMath.skyBrightness(level.getTimeOfDay(partial), level.getRainLevel(partial),
                level.dimensionType().hasSkyLight());
        int skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(cam));
        return LightMath.dayFactor(LightMath.daylight(sky, skyLight));
    }

    private static void quad(float x0, float y0, float x1, float y1) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(x0, y0, 0.0f);
        buffer.addVertex(x1, y0, 0.0f);
        buffer.addVertex(x1, y1, 0.0f);
        buffer.addVertex(x0, y1, 0.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private record State(int[] viewport, boolean blend, int blendSrcRgb, int blendDstRgb,
            int blendSrcAlpha, int blendDstAlpha, int blendEquationRgb, int blendEquationAlpha, boolean depthTest,
            int depthFunc, boolean depthMask, boolean cull, boolean[] colorMask, int activeTexture, int boundTexture,
            boolean scissor, int readFramebuffer, int drawFramebuffer) {
    }

    private static State saveState() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        ByteBuffer mask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        return new State(viewport, GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                new boolean[] {mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0},
                GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
    }

    private static void restoreState(State s) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, s.readFramebuffer());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, s.drawFramebuffer());
        RenderSystem.viewport(s.viewport()[0], s.viewport()[1], s.viewport()[2], s.viewport()[3]);
        RenderSystem.blendEquation(s.blendEquationRgb());
        if (s.blendEquationAlpha() != s.blendEquationRgb()) {
            GL20.glBlendEquationSeparate(s.blendEquationRgb(), s.blendEquationAlpha());
        }
        RenderSystem.blendFuncSeparate(s.blendSrcRgb(), s.blendDstRgb(), s.blendSrcAlpha(), s.blendDstAlpha());
        if (s.blend()) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        if (s.scissor()) {
            GlStateManager._enableScissorTest();
        } else {
            GlStateManager._disableScissorTest();
        }
        if (s.depthTest()) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthFunc(s.depthFunc());
        RenderSystem.depthMask(s.depthMask());
        if (s.cull()) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.colorMask(s.colorMask()[0], s.colorMask()[1], s.colorMask()[2], s.colorMask()[3]);
        GlStateManager._activeTexture(s.activeTexture());
        GlStateManager._bindTexture(s.boundTexture());
    }

    private static void abandonTimer(boolean timed) {
        if (timed && timerQuery != 0 && !timerPending) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        }
    }

    private static void beginTimer() {
        if (!GL.getCapabilities().OpenGL33) {
            return;
        }
        if (timerQuery == 0) {
            timerQuery = GL15.glGenQueries();
        }
        if (!timerPending) {
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, timerQuery);
        }
    }

    private static void endTimer(double startedNanos) {
        lastGpuTimed = false;
        lastMillis = (System.nanoTime() - startedNanos) / 1.0E6;
        if (!GL.getCapabilities().OpenGL33 || timerQuery == 0) {
            return;
        }
        if (!timerPending) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            timerPending = true;
        }
        if (GL15.glGetQueryObjecti(timerQuery, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            lastMillis = GL33.glGetQueryObjecti64(timerQuery, GL15.GL_QUERY_RESULT) / 1.0E6;
            lastGpuTimed = true;
            timerPending = false;
        }
    }
}
