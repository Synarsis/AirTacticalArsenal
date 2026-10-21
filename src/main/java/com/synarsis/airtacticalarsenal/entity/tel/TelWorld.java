package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Support;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Suspension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class TelWorld {

    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_breakable"));
    public static final ResourceKey<DamageType> STRIKE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_strike"));

    private static final int MAX_SMASHED_PER_PASS = 64;
    private static final double REACH_DOWN = 2.5;
    private static final double PATCH = 0.45;

    private TelWorld() {
    }

    public static DamageSource strike(Level level, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(STRIKE), direct, causing);
    }

    public static List<AABB> collisionBoxes(Level level, Entity self, Obb wallHull, double mx, double mz,
            boolean skipSoft) {
        AABB area = wallHull.worldAabb().expandTowards(mx, 0.0, mz).inflate(0.5).expandTowards(0.0, 1.0, 0.0);
        List<AABB> out = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        CollisionContext context = CollisionContext.of(self);
        int x0 = Mth.floor(area.minX);
        int x1 = Mth.floor(area.maxX);
        int y0 = Math.max(Mth.floor(area.minY), level.getMinBuildHeight());
        int y1 = Math.min(Mth.floor(area.maxY), level.getMaxBuildHeight() - 1);
        int z0 = Mth.floor(area.minZ);
        int z1 = Mth.floor(area.maxZ);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (!level.hasChunkAt(pos.set(bx, y0, bz))) {
                    continue;
                }
                for (int by = y0; by <= y1; by++) {
                    pos.set(bx, by, bz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || (skipSoft && state.is(BREAKABLE))) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, pos, context);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    final int ox = bx;
                    final int oy = by;
                    final int oz = bz;
                    shape.forAllBoxes((ax, ay, az, bxx, byy, bzz) ->
                            out.add(new AABB(ox + ax, oy + ay, oz + az, ox + bxx, oy + byy, oz + bzz)));
                }
            }
        }
        for (VoxelShape shape : level.getEntityCollisions(self, area)) {
            shape.forAllBoxes((ax, ay, az, bxx, byy, bzz) -> out.add(new AABB(ax, ay, az, bxx, byy, bzz)));
        }
        return out;
    }

    public record GroundScan(double[] wheelTop, Support[] wheelState, double[] skidTop, Support[] skidState) {

        public Suspension.Input input() {
            boolean[] wall = new boolean[Suspension.WHEELS];
            for (int i = 0; i < wall.length; i++) {
                wall[i] = this.wheelState[i] == Support.WALL;
            }
            return new Suspension.Input(this.wheelTop, wall, this.skidTop);
        }
    }

    @Nullable
    public static GroundScan scanGround(Level level, double x, double y, double z,
            float yaw, float pitch, float roll, double climb, boolean skipSoft) {
        double[] wheelTop = new double[Suspension.WHEELS];
        Support[] wheelState = new Support[Suspension.WHEELS];
        double[] skidTop = new double[Suspension.SKIDS];
        Support[] skidState = new Support[Suspension.SKIDS];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < Suspension.WHEELS; i++) {
            double lx = Suspension.wheelX(i);
            double lz = Suspension.wheelZ(i);
            if (!probe(level, x, y, z, yaw, pitch, roll, climb, skipSoft, lx, 0.0, lz, wheelTop, wheelState, i, pos)) {
                return null;
            }
        }
        for (int i = 0; i < Suspension.SKIDS; i++) {
            double lx = Suspension.skidX(i);
            double lz = Suspension.skidZ(i);
            if (!probe(level, x, y, z, yaw, pitch, roll, climb, skipSoft, lx, TelGeometry.BELLY, lz,
                    skidTop, skidState, i, pos)) {
                return null;
            }
        }
        return new GroundScan(wheelTop, wheelState, skidTop, skidState);
    }

    private static boolean probe(Level level, double x, double y, double z, float yaw, float pitch, float roll,
            double climb, boolean skipSoft, double lx, double ly, double lz, double[] tops, Support[] states, int index,
            BlockPos.MutableBlockPos pos) {
        double planeBottom = TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, new Vec3(lx, 0.0, lz)).y;
        double windowTop = Suspension.windowTop(planeBottom, climb);
        double windowBottom = TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, new Vec3(lx, ly, lz)).y
                - REACH_DOWN;

        double best = Double.NaN;
        boolean wall = false;
        for (int s = -1; s <= 1; s++) {
            Vec3 p = TelGeometry.localToWorld(x, y, z, yaw, 0.0f, 0.0f, new Vec3(lx, 0.0, lz + s * PATCH));
            pos.set(Mth.floor(p.x), Mth.floor(planeBottom), Mth.floor(p.z));
            if (!level.hasChunkAt(pos)) {
                return false;
            }
            double t = columnTop(level, p.x, p.z, windowTop, windowBottom, skipSoft, pos);
            Support kind = Support.classify(t, planeBottom, climb);
            if (kind == Support.WALL) {
                wall = true;
            } else if (kind == Support.CONTACT && (Double.isNaN(best) || t > best)) {
                best = t;
            }
        }
        if (wall) {
            states[index] = Support.WALL;
            tops[index] = Double.NaN;
        } else if (Double.isNaN(best)) {
            states[index] = Support.HANGING;
            tops[index] = Double.NaN;
        } else {
            states[index] = Support.CONTACT;
            tops[index] = best;
        }
        return true;
    }

    private static double columnTop(Level level, double x, double z, double yTop, double yBottom, boolean skipSoft,
            BlockPos.MutableBlockPos pos) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int from = Math.min(Mth.floor(yTop), level.getMaxBuildHeight() - 1);
        int to = Math.max(Mth.floor(yBottom), level.getMinBuildHeight());
        for (int by = from; by >= to; by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || (skipSoft && state.is(BREAKABLE))) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double fx = x - bx;
            double fz = z - bz;
            double top = Double.NEGATIVE_INFINITY;
            for (AABB box : shape.toAabbs()) {
                if (fx >= box.minX && fx <= box.maxX && fz >= box.minZ && fz <= box.maxZ) {
                    top = Math.max(top, box.maxY);
                }
            }
            if (top == Double.NEGATIVE_INFINITY) {
                continue;
            }
            return by + top;
        }
        return Double.NaN;
    }

    public static int smashSoftBlocks(ServerLevel level, Obb hull, Vec3 velocity, Entity breaker) {
        Obb reach = hull.translated(velocity.x, 0.5, velocity.z);
        AABB area = reach.worldAabb().inflate(0.25, 0.0, 0.25);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> doomed = new ArrayList<>();

        int x0 = Mth.floor(area.minX);
        int x1 = Mth.floor(area.maxX);
        int y0 = Mth.floor(area.minY);
        int y1 = Mth.floor(area.maxY);
        int z0 = Mth.floor(area.minZ);
        int z1 = Mth.floor(area.maxZ);
        for (int bx = x0; bx <= x1 && doomed.size() < MAX_SMASHED_PER_PASS; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = y0; by <= y1; by++) {
                    pos.set(bx, by, bz);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.is(BREAKABLE)) {
                        continue;
                    }
                    AABB cube = new AABB(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0).inflate(0.25, 0.0, 0.25);
                    if (ObbAabbSat.mtv(reach, cube) != null) {
                        doomed.add(pos.immutable());
                    }
                }
            }
        }
        for (BlockPos p : doomed) {
            level.destroyBlock(p, true, breaker);
        }
        return doomed.size();
    }
}
