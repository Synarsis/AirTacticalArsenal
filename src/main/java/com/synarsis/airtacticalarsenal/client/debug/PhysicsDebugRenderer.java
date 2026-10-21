package com.synarsis.airtacticalarsenal.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelJolt;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatch;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class PhysicsDebugRenderer {

    private static boolean enabled;

    private PhysicsDebugRenderer() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
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
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof IskanderTelEntity tel && tel.distanceToSqr(cam) < 96.0 * 96.0) {
                TelJolt jolt = tel.joltView();
                if (jolt != null && jolt.vehicle() != null) {
                    draw(pose, lines, tel, jolt);
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void draw(PoseStack pose, VertexConsumer lines, IskanderTelEntity tel, TelJolt jolt) {
        JoltVehicle vehicle = jolt.vehicle();
        VehicleSpec spec = vehicle.spec();
        float yaw = tel.getYRot();
        float pitch = tel.getXRot();
        float roll = tel.roll;
        double[][] r = PoseMath.rotation(yaw, pitch, roll);
        double[] origin = PoseMath.bodyOrigin(tel.getX(), tel.getY(), tel.getZ(), yaw, pitch, roll);
        for (VehicleSpec.Box box : spec.body.boxes) {
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    edge(pose, lines, origin, r, box, 0, a, b);
                    edge(pose, lines, origin, r, box, 1, a, b);
                    edge(pose, lines, origin, r, box, 2, a, b);
                }
            }
        }
        if (vehicle.wheeled()) {
            double restLength = Math.max(0.0, spec.suspension.travel - spec.suspension.restCompression);
            double attachY = spec.wheels.axleHeight + restLength;
            for (int i = 0; i < vehicle.wheelCount(); i++) {
                int axle = i >> 1;
                double lx = (i & 1) == 0 ? spec.wheels.trackHalf : -spec.wheels.trackHalf;
                double lz = spec.wheels.axleZ[axle];
                double wheelY = attachY - vehicle.wheelSuspension(i);
                double[] top = PoseMath.toWorld(origin, r, lx, attachY, lz);
                double[] bottom = PoseMath.toWorld(origin, r, lx, wheelY, lz);
                boolean contact = vehicle.wheelContact(i);
                line(pose, lines, new Vec3(top[0], top[1], top[2]), new Vec3(bottom[0], bottom[1], bottom[2]),
                        contact ? 0.2f : 1.0f, contact ? 1.0f : 0.3f, 0.2f);
                double load = Math.min(vehicle.wheelLoad(i) / 150000.0, 1.0);
                double[] tip = PoseMath.toWorld(origin, r, lx, wheelY + load * 1.5, lz);
                line(pose, lines, new Vec3(bottom[0], bottom[1], bottom[2]), new Vec3(tip[0], tip[1], tip[2]),
                        1.0f, 1.0f, 0.2f);
                double slip = Math.min(vehicle.wheelSlip(i), 1.0);
                double[] side = PoseMath.toWorld(origin, r, lx + Math.signum(lx) * slip * 1.5, wheelY, lz);
                line(pose, lines, new Vec3(bottom[0], bottom[1], bottom[2]), new Vec3(side[0], side[1], side[2]),
                        1.0f, 0.4f, 0.1f);
            }
        }
        if (jolt.ground() != null && jolt.ground().patch() != null) {
            drawPatch(pose, lines, jolt.ground().patch(), tel);
        }
    }

    private static void drawPatch(PoseStack pose, VertexConsumer lines, GroundPatch patch, IskanderTelEntity tel) {
        int cx = (int) Math.round((tel.getX() - patch.originX()) / patch.step());
        int cz = (int) Math.round((tel.getZ() - patch.originZ()) / patch.step());
        for (int iz = Math.max(0, cz - 16); iz <= Math.min(patch.count() - 1, cz + 16); iz += 2) {
            for (int ix = Math.max(0, cx - 16); ix <= Math.min(patch.count() - 1, cx + 16); ix += 2) {
                float h = patch.node(ix, iz);
                if (h == GroundPatch.HOLE) {
                    continue;
                }
                double x = patch.nodeX(ix);
                double z = patch.nodeZ(iz);
                line(pose, lines, new Vec3(x - 0.12, h, z), new Vec3(x + 0.12, h, z), 0.9f, 0.7f, 0.2f);
                line(pose, lines, new Vec3(x, h, z - 0.12), new Vec3(x, h, z + 0.12), 0.9f, 0.7f, 0.2f);
            }
        }
    }

    private static void edge(PoseStack pose, VertexConsumer lines, double[] origin, double[][] r, VehicleSpec.Box box,
            int axis, int a, int b) {
        double[] from = new double[3];
        double[] to = new double[3];
        int[] others = axis == 0 ? new int[] {1, 2} : (axis == 1 ? new int[] {0, 2} : new int[] {0, 1});
        for (int k = 0; k < 3; k++) {
            from[k] = box.centre[k];
            to[k] = box.centre[k];
        }
        from[others[0]] += a * box.half[others[0]];
        to[others[0]] += a * box.half[others[0]];
        from[others[1]] += b * box.half[others[1]];
        to[others[1]] += b * box.half[others[1]];
        from[axis] -= box.half[axis];
        to[axis] += box.half[axis];
        double[] p = PoseMath.toWorld(origin, r, from[0], from[1], from[2]);
        double[] q = PoseMath.toWorld(origin, r, to[0], to[1], to[2]);
        line(pose, lines, new Vec3(p[0], p[1], p[2]), new Vec3(q[0], q[1], q[2]), 0.2f, 0.9f, 0.9f);
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
}
