package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class LightDebug implements LayeredDraw.Layer {

    private static volatile boolean enabled;
    private static volatile boolean bufferView;
    private static volatile boolean voxels;

    private static final int VOXEL_RADIUS = 12;

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean bufferView() {
        return bufferView;
    }

    public static void toggleBufferView() {
        bufferView = !bufferView;
    }

    public static boolean voxels() {
        return voxels;
    }

    public static void toggleVoxels() {
        voxels = !voxels;
    }

    public static void render(RenderLevelStageEvent event) {
        if (voxels) {
            renderVoxels(event);
        }
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (AtaLights.Light light : AtaLights.all()) {
            double strength = light.strength(partial);
            if (strength <= 1.0E-3) {
                continue;
            }
            Vec3 p = light.position(partial);
            if (p.distanceToSqr(cam) > LightSelect.MAX_DISTANCE * LightSelect.MAX_DISTANCE) {
                continue;
            }
            float shade = (float) Math.min(1.0, 0.35 + strength / 8.0);
            float r = light.red() * shade;
            float g = light.green() * shade;
            float b = light.blue() * shade;
            double radius = light.radius(partial);
            if (light.kind() == AtaLights.Kind.SPOT) {
                cone(pose, lines, p, light.direction(), radius, light.cosOuter(), r, g, b);
            } else {
                sphere(pose, lines, p, radius, r, g, b);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderVoxels(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        if (!OccupancyVolume.updatedThisFrame()) {
            OccupancyVolume.update(mc.level, cam, java.util.List.of(cam));
        }
        OccupancyGrid grid = OccupancyVolume.grid();
        if (grid == null) {
            return;
        }
        Vec3 centre = mc.hitResult != null ? mc.hitResult.getLocation() : cam;
        int cx = (int) Math.floor(centre.x);
        int cy = (int) Math.floor(centre.y);
        int cz = (int) Math.floor(centre.z);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (int dx = -VOXEL_RADIUS; dx <= VOXEL_RADIUS; dx++) {
            for (int dy = -VOXEL_RADIUS; dy <= VOXEL_RADIUS; dy++) {
                for (int dz = -VOXEL_RADIUS; dz <= VOXEL_RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > VOXEL_RADIUS * VOXEL_RADIUS) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    int o = grid.opacity(x, y, z);
                    if (o <= 0 || !exposed(grid, x, y, z, o)) {
                        continue;
                    }
                    float r = o >= 255 ? 1.0f : o >= 200 ? 1.0f : o >= 140 ? 0.3f : 1.0f;
                    float g = o >= 255 ? 1.0f : o >= 200 ? 0.9f : o >= 140 ? 1.0f : 0.5f;
                    float b = o >= 255 ? 1.0f : o >= 200 ? 0.2f : o >= 140 ? 0.3f : 0.1f;
                    LevelRenderer.renderLineBox(pose, lines, x, y, z, x + 1.0, y + 1.0, z + 1.0, r, g, b, 1.0f);
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static boolean exposed(OccupancyGrid grid, int x, int y, int z, int opacity) {
        return grid.opacity(x + 1, y, z) < opacity || grid.opacity(x - 1, y, z) < opacity
                || grid.opacity(x, y + 1, z) < opacity || grid.opacity(x, y - 1, z) < opacity
                || grid.opacity(x, y, z + 1) < opacity || grid.opacity(x, y, z - 1) < opacity;
    }

    private static void sphere(PoseStack pose, VertexConsumer lines, Vec3 c, double radius, float r, float g, float b) {
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2.0 * i / steps;
            double a1 = Math.PI * 2.0 * (i + 1) / steps;
            double c0 = Math.cos(a0) * radius;
            double s0 = Math.sin(a0) * radius;
            double c1 = Math.cos(a1) * radius;
            double s1 = Math.sin(a1) * radius;
            line(pose, lines, c.add(c0, 0.0, s0), c.add(c1, 0.0, s1), r, g, b);
            line(pose, lines, c.add(c0, s0, 0.0), c.add(c1, s1, 0.0), r, g, b);
            line(pose, lines, c.add(0.0, c0, s0), c.add(0.0, c1, s1), r, g, b);
        }
    }

    private static void cone(PoseStack pose, VertexConsumer lines, Vec3 apex, Vec3 axis, double reach, double cosOuter,
            float r, float g, float b) {
        Vec3 up = Math.abs(axis.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = axis.cross(up).normalize();
        Vec3 above = right.cross(axis).normalize();
        double sin = Math.sqrt(Math.max(0.0, 1.0 - cosOuter * cosOuter));
        Vec3 centre = apex.add(axis.scale(reach * cosOuter));
        double rim = reach * sin;
        int steps = 24;
        Vec3 previous = null;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2.0 * i / steps;
            Vec3 point = centre.add(right.scale(Math.cos(a) * rim)).add(above.scale(Math.sin(a) * rim));
            if (previous != null) {
                line(pose, lines, previous, point, r, g, b);
            }
            if (i % 4 == 0) {
                line(pose, lines, apex, point, r, g, b);
            }
            previous = point;
        }
        line(pose, lines, apex, apex.add(axis.scale(reach)), r, g, b);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 a, Vec3 b, float r, float g, float bl) {
        PoseStack.Pose last = pose.last();
        Vec3 d = b.subtract(a);
        double len = d.length();
        float nx = len > 1.0E-6 ? (float) (d.x / len) : 0.0f;
        float ny = len > 1.0E-6 ? (float) (d.y / len) : 1.0f;
        float nz = len > 1.0E-6 ? (float) (d.z / len) : 0.0f;
        lines.addVertex(last, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1.0f).setNormal(last, nx, ny, nz);
        lines.addVertex(last, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1.0f).setNormal(last, nx, ny, nz);
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        if (!enabled && !bufferView && !voxels) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String[] rows = {
                String.format(Locale.ROOT, "lights %d in the list, %d drawn (%d with block shadows), %d haze patches",
                        AtaLights.all().size(), LightPass.lastLights(), LightPass.lastShadowLights(),
                        LightPass.lastHazePatches()),
                String.format(Locale.ROOT, "pass %.2f ms (%s), %s buffers", LightPass.lastMillis(),
                        LightPass.gpuTimed() ? "gpu" : "cpu", LightPass.floatBuffers() ? "16-bit" : "8-bit fallback"),
                LightTimings.shared().line(),
                ShaderPackGuard.shaderPackInUse() ? "a shader pack is in use: the pass is off" : "no shader pack",
                (bufferView ? "showing the light buffer; " : "") + (voxels ? "outlining the solidity volume; " : "")
                        + (OccupancyVolume.active() ? "volume on" : "volume off") + ", " + LightPass.lastBoxes() + " entity boxes"};
        int y = 4;
        for (String row : rows) {
            g.drawString(mc.font, row, 4, y, 0xFFFFD34D, true);
            y += 10;
        }
    }
}
