package com.synarsis.airtacticalarsenal.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.synarsis.airtacticalarsenal.combat.hitbox.ObbProvider;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.patriot.M983Coupling;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotClearance;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotHitbox;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotPose;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelClearance;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelWorld;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Support;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Suspension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class HitboxDebugRenderer {

    private static boolean enabled = com.synarsis.airtacticalarsenal.config.AtaClientConfig
            .isHitboxOverlayEnabled();

    private HitboxDebugRenderer() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        com.synarsis.airtacticalarsenal.config.AtaClientConfig.setHitboxOverlay(value);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void render(RenderLevelStageEvent event) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof IskanderTelEntity tel) {
                if (tel.distanceToSqr(cam) < 96.0 * 96.0) {
                    drawLauncher(pose, lines, tel, partialTick);
                    if (mc.player != null && mc.player.getVehicle() == tel) {
                        drawClearance(pose, lines, tel);
                    }
                    for (com.synarsis.airtacticalarsenal.combat.hitbox.Obb obb : tel.getRenderObbs(partialTick)) {
                        drawBox(pose, lines, obb);
                    }
                }
                continue;
            }
            if (entity instanceof PatriotVehicleEntity patriot && mc.player != null
                    && mc.player.getVehicle() == patriot && patriot.distanceToSqr(cam) < 96.0 * 96.0) {
                drawPatriotCells(pose, lines, patriot);
            }
            if (entity instanceof M983TractorEntity tractor && tractor.distanceToSqr(cam) < 96.0 * 96.0) {
                drawSaddle(pose, lines, tractor, partialTick);
            }
            if (entity instanceof PatriotVehicleEntity trailer && !trailer.isHitched()
                    && trailer.distanceToSqr(cam) < 96.0 * 96.0) {
                drawKingpin(pose, lines, trailer, partialTick);
            }
            if (!(entity instanceof ObbProvider provider) || provider.useAabb()) {
                continue;
            }
            for (com.synarsis.airtacticalarsenal.combat.hitbox.Obb obb : provider.getRenderObbs(partialTick)) {
                drawBox(pose, lines, obb);
            }
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawLauncher(PoseStack pose, VertexConsumer lines, IskanderTelEntity tel, float pt) {
        double x = Mth.lerp(pt, tel.xo, tel.getX());
        double y = Mth.lerp(pt, tel.yo, tel.getY());
        double z = Mth.lerp(pt, tel.zo, tel.getZ());
        float yaw = Mth.rotLerp(pt, tel.yRotO, tel.getYRot());
        float pitch = Mth.lerp(pt, tel.xRotO, tel.getXRot());
        float roll = Mth.lerp(pt, tel.rollO, tel.roll);
        double climb = TelGeometry.CLIMB;
        try {
            climb = ShahedConfig.getTelClimb();
        } catch (IllegalStateException ignored) {
        }

        Obb hull = Obb.wallHull(x, y, z, yaw, pitch, roll);
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                line(pose, lines, hull.point(a * hull.half(0), b * hull.half(1), -hull.half(2)),
                        hull.point(a * hull.half(0), b * hull.half(1), hull.half(2)), 1.0f, 0.4f, 0.0f);
                line(pose, lines, hull.point(a * hull.half(0), -hull.half(1), b * hull.half(2)),
                        hull.point(a * hull.half(0), hull.half(1), b * hull.half(2)), 1.0f, 0.4f, 0.0f);
                line(pose, lines, hull.point(-hull.half(0), a * hull.half(1), b * hull.half(2)),
                        hull.point(hull.half(0), a * hull.half(1), b * hull.half(2)), 1.0f, 0.4f, 0.0f);
            }
        }

        TelWorld.GroundScan scan = tel.getLastScan();
        if (scan == null) {
            return;
        }
        for (int i = 0; i < Suspension.WHEELS; i++) {
            Vec3 spot = TelGeometry.localToWorld(x, y, z, yaw, 0.0f, 0.0f,
                    new Vec3(Suspension.wheelX(i), 0.0, Suspension.wheelZ(i)));
            double bottom = Suspension.bottom(y, pitch, roll, i);
            drawProbe(pose, lines, spot, bottom, scan.wheelState()[i], scan.wheelTop()[i],
                    Suspension.windowTop(bottom, climb), 0.3);
        }
        for (int i = 0; i < Suspension.SKIDS; i++) {
            Vec3 spot = TelGeometry.localToWorld(x, y, z, yaw, 0.0f, 0.0f,
                    new Vec3(Suspension.skidX(i), 0.0, Suspension.skidZ(i)));
            double bottom = Suspension.skidBottom(y, pitch, roll, i);
            double plane = Suspension.bottom(y, pitch, roll, 0);
            drawProbe(pose, lines, spot, bottom, scan.skidState()[i], scan.skidTop()[i],
                    Suspension.windowTop(plane, climb), 0.15);
        }
    }

    private static void drawSaddle(PoseStack pose, VertexConsumer lines, M983TractorEntity tractor, float pt) {
        Vec3 saddle = M983Coupling.worldOf(Mth.lerp(pt, tractor.xo, tractor.getX()),
                Mth.lerp(pt, tractor.yo, tractor.getY()), Mth.lerp(pt, tractor.zo, tractor.getZ()),
                Mth.rotLerp(pt, tractor.yRotO, tractor.getYRot()), Mth.lerp(pt, tractor.xRotO, tractor.getXRot()),
                tractor.getRollLerp(pt), M983Coupling.saddleAnchor());
        M983Coupling.Hint hint = tractor.couplingHint();
        boolean ready = hint != null && hint.ready();
        float r = 0.2f;
        float g = ready ? 1.0f : 0.8f;
        float b = ready ? 0.2f : 1.0f;
        cross(pose, lines, saddle, 0.35, r, g, b);
        circle(pose, lines, saddle, M983Coupling.CAPTURE_DISTANCE, r, g, b);
        if (hint != null) {
            PatriotVehicleEntity trailer = tractor.level().getEntity(hint.trailerId()) instanceof PatriotVehicleEntity t
                    ? t : null;
            if (trailer != null) {
                line(pose, lines, saddle, M983Coupling.kingpinWorld(trailer), ready ? 0.2f : 1.0f, ready ? 1.0f : 0.5f,
                        0.1f);
            }
        }
    }

    private static void drawKingpin(PoseStack pose, VertexConsumer lines, PatriotVehicleEntity trailer, float pt) {
        Vec3 pin = M983Coupling.worldOf(Mth.lerp(pt, trailer.xo, trailer.getX()),
                Mth.lerp(pt, trailer.yo, trailer.getY()), Mth.lerp(pt, trailer.zo, trailer.getZ()),
                Mth.rotLerp(pt, trailer.yRotO, trailer.getYRot()), Mth.lerp(pt, trailer.xRotO, trailer.getXRot()),
                trailer.getRollLerp(pt), M983Coupling.kingpinAnchor());
        boolean free = trailer.canBeCoupledNow();
        float r = 1.0f;
        float g = free ? 0.6f : 0.2f;
        float b = 0.1f;
        cross(pose, lines, pin, 0.35, r, g, b);
        circle(pose, lines, pin, M983Coupling.CAPTURE_DISTANCE, r, g, b);
        line(pose, lines, new Vec3(pin.x + M983Coupling.CAPTURE_DISTANCE, pin.y - M983Coupling.CAPTURE_HEIGHT, pin.z),
                new Vec3(pin.x + M983Coupling.CAPTURE_DISTANCE, pin.y + M983Coupling.CAPTURE_HEIGHT, pin.z), r, g, b);
        line(pose, lines, new Vec3(pin.x - M983Coupling.CAPTURE_DISTANCE, pin.y - M983Coupling.CAPTURE_HEIGHT, pin.z),
                new Vec3(pin.x - M983Coupling.CAPTURE_DISTANCE, pin.y + M983Coupling.CAPTURE_HEIGHT, pin.z), r, g, b);
    }

    private static void cross(PoseStack pose, VertexConsumer lines, Vec3 at, double arm, float r, float g, float b) {
        line(pose, lines, new Vec3(at.x - arm, at.y, at.z), new Vec3(at.x + arm, at.y, at.z), r, g, b);
        line(pose, lines, new Vec3(at.x, at.y - arm, at.z), new Vec3(at.x, at.y + arm, at.z), r, g, b);
        line(pose, lines, new Vec3(at.x, at.y, at.z - arm), new Vec3(at.x, at.y, at.z + arm), r, g, b);
    }

    private static void circle(PoseStack pose, VertexConsumer lines, Vec3 centre, double radius, float r, float g,
            float b) {
        int segments = 32;
        Vec3 previous = new Vec3(centre.x + radius, centre.y, centre.z);
        for (int i = 1; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            Vec3 next = new Vec3(centre.x + radius * Math.cos(angle), centre.y, centre.z + radius * Math.sin(angle));
            line(pose, lines, previous, next, r, g, b);
            previous = next;
        }
    }

    private static void drawPatriotCells(PoseStack pose, VertexConsumer lines, PatriotVehicleEntity patriot) {
        PatriotHitbox model = PatriotHitbox.of(patriot.kind());
        PatriotPose posed = patriot.hitboxPose();
        TelClearance.Pose at = patriot.clearancePose();
        TelClearance.Solid solid = patriot.worldSolid();
        java.util.Set<net.minecraft.core.BlockPos> cells = new java.util.HashSet<>();
        cells.addAll(PatriotClearance.supportCells(model, posed, at));
        cells.addAll(PatriotClearance.liftCells(model, posed, at));
        for (net.minecraft.core.BlockPos p : cells) {
            if (solid.at(p.getX(), p.getY(), p.getZ())) {
                LevelRenderer.renderLineBox(pose, lines, p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 1.0,
                        p.getZ() + 1.0, 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }
    }

    private static void drawClearance(PoseStack pose, VertexConsumer lines, IskanderTelEntity tel) {
        TelClearance.Pose at = tel.clearancePose();
        TelClearance.Solid solid = tel.worldSolid();

        outline(pose, lines, at, TelClearance.roofOutline(), 0.25f, 0.75f, 0.95f);
        for (int slot = 0; slot < 2; slot++) {
            outline(pose, lines, at, TelClearance.arcOutline(slot), 0.95f, 0.8f, 0.25f);
            outline(pose, lines, at, TelClearance.skyOutline(slot), 0.55f, 0.55f, 0.95f);
            Vec3 base = TelGeometry.MISSILE_BASE[slot];
            Vec3 tip = at.toWorld(new Vec3(base.x, TelGeometry.MISSILE_TOP_Y, base.z));
            LevelRenderer.renderLineBox(pose, lines, Mth.floor(tip.x) - 1, Mth.floor(tip.y) + 1, Mth.floor(tip.z) - 1,
                    Mth.floor(tip.x) + 2, Mth.floor(tip.y) + 1 + tel.skyClearance(), Mth.floor(tip.z) + 2,
                    0.55f, 0.55f, 0.95f, 1.0f);
        }

        java.util.Set<net.minecraft.core.BlockPos> cells = new java.util.HashSet<>();
        cells.addAll(TelClearance.blocks(TelClearance.roofCells(), at));
        for (int slot = 0; slot < 2; slot++) {
            cells.addAll(TelClearance.blocks(TelClearance.arcCells(slot), at));
            cells.addAll(TelClearance.skyBlocks(slot, at, tel.skyClearance()));
        }
        for (net.minecraft.core.BlockPos p : cells) {
            if (solid.at(p.getX(), p.getY(), p.getZ())) {
                LevelRenderer.renderLineBox(pose, lines, p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 1.0,
                        p.getZ() + 1.0, 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }
    }

    private static void outline(PoseStack pose, VertexConsumer lines, TelClearance.Pose at, java.util.List<Vec3[]> segments,
            float r, float g, float b) {
        for (Vec3[] s : segments) {
            line(pose, lines, at.toWorld(s[0]), at.toWorld(s[1]), r, g, b);
        }
    }

    private static void drawProbe(PoseStack pose, VertexConsumer lines, Vec3 spot, double bottom, Support state,
            double top, double windowTop, double arm) {
        double at;
        float r;
        float g;
        float b = 0.1f;
        switch (state) {
            case CONTACT -> {
                at = top;
                r = 0.2f;
                g = 1.0f;
                b = 0.2f;
            }
            case WALL -> {
                at = windowTop;
                r = 1.0f;
                g = 0.9f;
            }
            default -> {
                at = bottom;
                r = 1.0f;
                g = 0.1f;
            }
        }
        line(pose, lines, new Vec3(spot.x, bottom, spot.z), new Vec3(spot.x, at, spot.z), r, g, b);
        line(pose, lines, new Vec3(spot.x - arm, at, spot.z), new Vec3(spot.x + arm, at, spot.z), r, g, b);
        line(pose, lines, new Vec3(spot.x, at, spot.z - arm), new Vec3(spot.x, at, spot.z + arm), r, g, b);
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

    private static void drawBox(PoseStack pose, VertexConsumer lines, com.synarsis.airtacticalarsenal.combat.hitbox.Obb obb) {
        Vector3f half = obb.halfExtents();
        int rgb = obb.tag().debugColor();
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        pose.pushPose();
        pose.translate(obb.center().x, obb.center().y, obb.center().z);
        pose.mulPose(obb.rotation());
        LevelRenderer.renderLineBox(pose, lines,
                -half.x, -half.y, -half.z, half.x, half.y, half.z,
                r, g, b, 1.0f);
        pose.popPose();
    }
}
