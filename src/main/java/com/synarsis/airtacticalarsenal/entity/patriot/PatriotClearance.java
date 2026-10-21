package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelClearance;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class PatriotClearance {

    public static final double TILT_LIMIT_DEGREES = 6.0;
    public static final double FOOT_REACH = 1.0;
    public static final double WHEEL_REACH = 0.6;
    public static final double TRACTOR_STEP_PX = 12.0;
    public static final double CELL_SHRINK = 0.03;
    public static final double FOOT_SHRINK = 0.12;
    public static final double FOOT_LIFT = 0.12;
    public static final double TRACTOR_LIFT = 0.15;
    public static final double[] LIFT_FRACTIONS = {0.0, 0.25, 0.5, 0.75, 1.0};

    public record Blocker(PatriotRefusal reason, BlockPos at, String detail) {

        public Blocker(PatriotRefusal reason, BlockPos at) {
            this(reason, at, "");
        }

        public PatriotRefusal.Denial denial() {
            return new PatriotRefusal.Denial(this.reason, TelClearance.describe(this.at));
        }
    }

    private PatriotClearance() {
    }

    public static boolean tiltOk(float pitch, float roll) {
        return Math.abs(Mth.wrapDegrees(pitch)) < TILT_LIMIT_DEGREES
                && Math.abs(Mth.wrapDegrees(roll)) < TILT_LIMIT_DEGREES;
    }

    public static PatriotPose withSupportsOut(PatriotKind kind, PatriotPose pose) {
        return pose.withParts(new TelHitboxPose.Part(kind.clipPrefix() + "supports_out", TelPose.HELD_SECONDS),
                pose.lift(), pose.service(), pose.doors());
    }

    public static Set<BlockPos> cellsOf(List<WorldBox> boxes, double shrink) {
        Set<BlockPos> out = new HashSet<>();
        for (WorldBox box : boxes) {
            addCells(box, shrink, out);
        }
        return out;
    }

    private static void addCells(WorldBox box, double shrink, Set<BlockPos> out) {
        double hx = Math.max(0.005, box.hx() - shrink);
        double hy = Math.max(0.005, box.hy() - shrink);
        double hz = Math.max(0.005, box.hz() - shrink);
        Obb obb = Obb.of(box.centre(), box.ax(), box.ay(), box.az(), hx, hy, hz);
        AABB area = obb.worldAabb();
        int x0 = Mth.floor(area.minX);
        int y0 = Mth.floor(area.minY);
        int z0 = Mth.floor(area.minZ);
        int x1 = Mth.floor(area.maxX);
        int y1 = Mth.floor(area.maxY);
        int z1 = Mth.floor(area.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (out.contains(cell)) {
                        continue;
                    }
                    if (ObbAabbSat.mtv(obb, new AABB(x, y, z, x + 1, y + 1, z + 1)) != null) {
                        out.add(cell);
                    }
                }
            }
        }
    }

    private static List<WorldBox> worldBoxes(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at,
            Predicate<String> bone, boolean onlySolid) {
        List<WorldBox> out = new ArrayList<>();
        for (ModelHitbox.LocalBox box : model.localBoxes(pose)) {
            if (onlySolid && !box.solid()) {
                continue;
            }
            if (bone.test(model.boneName(box.bone()))) {
                out.add(ModelHitbox.toWorld(box, at.x(), at.y(), at.z(), at.yaw(), at.pitch(), at.roll()));
            }
        }
        return out;
    }

    private static List<WorldBox> liftedFeet(List<WorldBox> feet) {
        return lifted(feet, FOOT_LIFT);
    }

    private static List<WorldBox> lifted(List<WorldBox> boxes, double dy) {
        List<WorldBox> out = new ArrayList<>(boxes.size());
        for (WorldBox box : boxes) {
            out.add(new WorldBox(box.zone(), box.centre().add(0.0, dy, 0.0), box.ax(), box.ay(), box.az(),
                    box.hx(), box.hy(), box.hz(), box.solid()));
        }
        return out;
    }

    public static Set<BlockPos> supportCells(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at) {
        PatriotPose out = withSupportsOut(model.kind(), pose);
        return cellsOf(liftedFeet(worldBoxes(model, out, at, b -> b.startsWith("jack_foot_"), false)), FOOT_SHRINK);
    }

    @Nullable
    public static Blocker supportBlocker(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at,
            TelClearance.Solid solid) {
        PatriotPose out = withSupportsOut(model.kind(), pose);
        Map<String, List<WorldBox>> feet = new LinkedHashMap<>();
        for (ModelHitbox.LocalBox box : model.localBoxes(out)) {
            String name = model.boneName(box.bone());
            if (name.startsWith("jack_foot_")) {
                feet.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(ModelHitbox.toWorld(box, at.x(), at.y(), at.z(), at.yaw(), at.pitch(), at.roll()));
            }
        }
        for (List<WorldBox> foot : feet.values()) {
            BlockPos inside = TelClearance.firstBlocked(cellsOf(liftedFeet(foot), FOOT_SHRINK), at, solid);
            if (inside != null) {
                return new Blocker(PatriotRefusal.SUPPORTS_NO_GROUND, inside, "foot in a block");
            }
            double bottom = Double.MAX_VALUE;
            double cx = 0.0;
            double cz = 0.0;
            for (WorldBox box : foot) {
                bottom = Math.min(bottom, box.minY());
                cx += box.centre().x;
                cz += box.centre().z;
            }
            cx /= foot.size();
            cz /= foot.size();
            if (!hasGroundBelow(solid, cx, bottom, cz, FOOT_REACH)) {
                return new Blocker(PatriotRefusal.SUPPORTS_NO_GROUND,
                        new BlockPos(Mth.floor(cx), Mth.floor(bottom - 0.05), Mth.floor(cz)),
                        "no ground under the foot at " + cx + " " + bottom + " " + cz);
            }
        }
        return null;
    }

    private static boolean hasGroundBelow(TelClearance.Solid solid, double x, double bottom, double z, double reach) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (double dy = -0.05; dy <= reach + 1.0E-9; dy += 0.25) {
            if (solid.at(bx, Mth.floor(bottom - dy), bz)) {
                return true;
            }
        }
        return solid.at(bx, Mth.floor(bottom - reach), bz);
    }

    private static boolean liftBone(PatriotKind kind, String bone) {
        PatriotZone zone = PatriotZone.ofBone(bone);
        return kind.isLauncher() ? zone == PatriotZone.PACK : zone == PatriotZone.ANTENNA;
    }

    public static Set<BlockPos> liftCells(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at) {
        Set<BlockPos> out = new HashSet<>();
        for (PatriotPose step : liftPoses(model, pose)) {
            out.addAll(cellsOf(worldBoxes(model, step, at, b -> liftBone(model.kind(), b), true), CELL_SHRINK));
        }
        return out;
    }

    private static List<PatriotPose> liftPoses(PatriotHitbox model, PatriotPose pose) {
        PatriotKind kind = model.kind();
        String clip = PatriotBones.clipName(kind, PatriotBones.LIFT, TelPose.Clip.OPENING);
        double length = model.sampler().length(clip);
        PatriotPose supported = withSupportsOut(kind, pose);
        List<PatriotPose> poses = new ArrayList<>();
        for (double fraction : LIFT_FRACTIONS) {
            poses.add(supported.withParts(supported.supports(), new TelHitboxPose.Part(clip, length * fraction),
                    supported.service(), supported.doors()));
        }
        return poses;
    }

    @Nullable
    public static Blocker liftBlocker(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at,
            TelClearance.Solid solid) {
        for (PatriotPose step : liftPoses(model, pose)) {
            BlockPos blocked = TelClearance.firstBlocked(
                    cellsOf(worldBoxes(model, step, at, b -> liftBone(model.kind(), b), true), CELL_SHRINK), at,
                    solid);
            if (blocked != null) {
                return new Blocker(PatriotRefusal.LIFT_BLOCKED, blocked);
            }
        }
        return null;
    }

    private static boolean tractorBone(String bone) {
        return bone.startsWith("truck_") || bone.equals("tractor_carrier");
    }

    private static List<Double> tractorSteps() {
        List<Double> out = new ArrayList<>();
        for (double px = 0.0; px < PatriotGeometry.TRACTOR_OFFSET_PX; px += TRACTOR_STEP_PX) {
            out.add(px);
        }
        out.add(PatriotGeometry.TRACTOR_OFFSET_PX);
        return out;
    }

    public static Set<BlockPos> tractorCells(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at) {
        Set<BlockPos> out = new HashSet<>();
        for (double px : tractorSteps()) {
            PatriotPose step = pose.withTractor(px, pose.latchDegrees());
            out.addAll(cellsOf(lifted(worldBoxes(model, step, at, PatriotClearance::tractorBone, true), TRACTOR_LIFT),
                    CELL_SHRINK));
        }
        return out;
    }

    @Nullable
    public static Blocker tractorBlocker(PatriotHitbox model, PatriotPose pose, TelClearance.Pose at,
            TelClearance.Solid solid) {
        for (double px : tractorSteps()) {
            PatriotPose step = pose.withTractor(px, pose.latchDegrees());
            BlockPos blocked = TelClearance.firstBlocked(
                    cellsOf(lifted(worldBoxes(model, step, at, PatriotClearance::tractorBone, true), TRACTOR_LIFT),
                            CELL_SHRINK), at, solid);
            if (blocked != null) {
                return new Blocker(PatriotRefusal.TRACTOR_NO_ROOM, blocked, "path at " + px + " px");
            }
        }
        PatriotPose end = pose.withTractor(PatriotGeometry.TRACTOR_OFFSET_PX, pose.latchDegrees());
        Map<String, List<WorldBox>> wheels = new LinkedHashMap<>();
        for (ModelHitbox.LocalBox box : model.localBoxes(end)) {
            String name = model.boneName(box.bone());
            if (name.startsWith("truck_wheel_")) {
                wheels.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(ModelHitbox.toWorld(box, at.x(), at.y(), at.z(), at.yaw(), at.pitch(), at.roll()));
            }
        }
        for (List<WorldBox> wheel : wheels.values()) {
            double bottom = Double.MAX_VALUE;
            double cx = 0.0;
            double cz = 0.0;
            for (WorldBox box : wheel) {
                bottom = Math.min(bottom, box.minY());
                cx += box.centre().x;
                cz += box.centre().z;
            }
            cx /= wheel.size();
            cz /= wheel.size();
            if (!hasGroundBelow(solid, cx, bottom, cz, WHEEL_REACH)) {
                return new Blocker(PatriotRefusal.TRACTOR_NO_ROOM,
                        new BlockPos(Mth.floor(cx), Mth.floor(bottom - 0.05), Mth.floor(cz)),
                        "no ground under a wheel at " + cx + " " + bottom + " " + cz);
            }
        }
        return null;
    }
}
