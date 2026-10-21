package com.synarsis.airtacticalarsenal.entity.tel;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TelClearance {

    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    public record Pose(double x, double y, double z, float yaw, float pitch, float roll) {
        public Vec3 toWorld(Vec3 local) {
            return TelGeometry.localToWorld(this.x, this.y, this.z, this.yaw, this.pitch, this.roll, local);
        }
    }

    private static final double ROOF_X_MIN = 1.7;
    private static final double ROOF_X_MAX = 3.3;
    private static final double ROOF_Y_MIN = 2.8;
    private static final double ROOF_Y_MAX = 4.6;
    private static final double ROOF_Z_MIN = -6.9;
    private static final double ROOF_Z_MAX = 2.6;

    private static final double PIVOT_Y = 1.5;
    private static final double PIVOT_Z = -5.9;
    private static final double ROUND_LENGTH = 8.0;
    private static final double ROUND_RADIUS = 0.7;
    private static final double SWEEP_FLOOR_Y = 1.0;

    public static final double SKY_FROM_Y = 3.6;

    private static final double STEP = 0.5;

    private static final List<Vec3> ROOF = buildRoof();
    private static final List<List<Vec3>> ARC = List.of(buildArc(0), buildArc(1));
    private static final List<List<Vec3>> SKY_LOCAL = List.of(buildSkyLocal(0), buildSkyLocal(1));

    private TelClearance() {
    }

    private static List<Double> span(double from, double to, double step) {
        List<Double> out = new ArrayList<>();
        for (double v = from; v < to + 1.0E-9; v += step) {
            out.add(v);
        }
        if (out.isEmpty() || out.get(out.size() - 1) < to - 0.25 * step) {
            out.add(to);
        }
        return out;
    }

    private static List<Vec3> buildRoof() {
        List<Vec3> out = new ArrayList<>();
        for (double sign : new double[] {1.0, -1.0}) {
            for (double x : span(ROOF_X_MIN, ROOF_X_MAX, STEP)) {
                for (double y : span(ROOF_Y_MIN, ROOF_Y_MAX, STEP)) {
                    for (double z : span(ROOF_Z_MIN, ROOF_Z_MAX, STEP)) {
                        out.add(new Vec3(sign * x, y, z));
                    }
                }
            }
        }
        return out;
    }

    private static List<Vec3> buildArc(int slot) {
        double x = TelGeometry.MISSILE_BASE[slot].x;
        Set<Long> seen = new HashSet<>();
        List<Vec3> out = new ArrayList<>();
        double[] offsets = {-ROUND_RADIUS, 0.0, ROUND_RADIUS};
        for (int degrees = 0; degrees <= 90; degrees += 5) {
            double a = Math.toRadians(degrees);
            double dy = Math.sin(a);
            double dz = Math.cos(a);
            for (double t = 0.0; t <= ROUND_LENGTH + 1.0E-9; t += STEP) {
                double cy = PIVOT_Y + dy * t;
                double cz = PIVOT_Z + dz * t;
                for (double ox : offsets) {
                    for (double oy : offsets) {
                        for (double oz : offsets) {
                            double py = cy + oy;
                            if (py < SWEEP_FLOOR_Y) {
                                continue;
                            }
                            long key = (Math.round((x + ox) * 4.0) & 0xFFFFL) << 32
                                    | (Math.round(py * 4.0) & 0xFFFFL) << 16
                                    | (Math.round((cz + oz) * 4.0) & 0xFFFFL);
                            if (seen.add(key)) {
                                out.add(new Vec3(x + ox, py, cz + oz));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<Vec3> buildSkyLocal(int slot) {
        Vec3 base = TelGeometry.MISSILE_BASE[slot];
        List<Vec3> out = new ArrayList<>();
        for (double y = SKY_FROM_Y; y <= TelGeometry.MISSILE_TOP_Y + 1.0E-9; y += 1.0) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(new Vec3(base.x + dx, y, base.z + dz));
                }
            }
        }
        return out;
    }

    public static List<Vec3> roofCells() {
        return ROOF;
    }

    public static List<Vec3> arcCells(int slot) {
        return ARC.get(slot);
    }

    public static List<Vec3> skyCellsLocal(int slot) {
        return SKY_LOCAL.get(slot);
    }

    public static Set<BlockPos> blocks(List<Vec3> local, Pose pose) {
        Set<BlockPos> out = new HashSet<>();
        for (Vec3 p : local) {
            Vec3 w = pose.toWorld(p);
            out.add(new BlockPos(Mth.floor(w.x), Mth.floor(w.y), Mth.floor(w.z)));
        }
        return out;
    }

    public static Set<BlockPos> skyBlocks(int slot, Pose pose, int clearance) {
        Set<BlockPos> out = blocks(SKY_LOCAL.get(slot), pose);
        Vec3 base = TelGeometry.MISSILE_BASE[slot];
        Vec3 tip = pose.toWorld(new Vec3(base.x, TelGeometry.MISSILE_TOP_Y, base.z));
        int bx = Mth.floor(tip.x);
        int bz = Mth.floor(tip.z);
        int from = Mth.floor(tip.y) + 1;
        for (int dy = 0; dy < Math.max(0, clearance); dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(new BlockPos(bx + dx, from + dy, bz + dz));
                }
            }
        }
        return out;
    }

    public static List<Vec3[]> boxEdges(double x0, double y0, double z0, double x1, double y1, double z1) {
        List<Vec3[]> out = new ArrayList<>();
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                double u = a == 0 ? 0.0 : 1.0;
                double v = b == 0 ? 0.0 : 1.0;
                out.add(new Vec3[] {new Vec3(x0, y0 + (y1 - y0) * u, z0 + (z1 - z0) * v),
                        new Vec3(x1, y0 + (y1 - y0) * u, z0 + (z1 - z0) * v)});
                out.add(new Vec3[] {new Vec3(x0 + (x1 - x0) * u, y0, z0 + (z1 - z0) * v),
                        new Vec3(x0 + (x1 - x0) * u, y1, z0 + (z1 - z0) * v)});
                out.add(new Vec3[] {new Vec3(x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, z0),
                        new Vec3(x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, z1)});
            }
        }
        return out;
    }

    public static List<Vec3[]> roofOutline() {
        List<Vec3[]> out = new ArrayList<>();
        out.addAll(boxEdges(ROOF_X_MIN, ROOF_Y_MIN, ROOF_Z_MIN, ROOF_X_MAX, ROOF_Y_MAX, ROOF_Z_MAX));
        out.addAll(boxEdges(-ROOF_X_MAX, ROOF_Y_MIN, ROOF_Z_MIN, -ROOF_X_MIN, ROOF_Y_MAX, ROOF_Z_MAX));
        return out;
    }

    public static List<Vec3[]> arcOutline(int slot) {
        double x = TelGeometry.MISSILE_BASE[slot].x;
        double reach = ROUND_LENGTH + ROUND_RADIUS;
        List<Vec3[]> out = new ArrayList<>();
        Vec3 pivot = new Vec3(x, PIVOT_Y, PIVOT_Z);
        Vec3 previous = null;
        for (int degrees = 0; degrees <= 90; degrees += 10) {
            double a = Math.toRadians(degrees);
            Vec3 tip = new Vec3(x, PIVOT_Y + Math.sin(a) * reach, PIVOT_Z + Math.cos(a) * reach);
            if (previous != null) {
                out.add(new Vec3[] {previous, tip});
            }
            previous = tip;
        }
        out.add(new Vec3[] {pivot, new Vec3(x, PIVOT_Y, PIVOT_Z + reach)});
        out.add(new Vec3[] {pivot, new Vec3(x, PIVOT_Y + reach, PIVOT_Z)});
        return out;
    }

    public static List<Vec3[]> skyOutline(int slot) {
        Vec3 base = TelGeometry.MISSILE_BASE[slot];
        return boxEdges(base.x - 1.5, SKY_FROM_Y, base.z - 1.5, base.x + 1.5, TelGeometry.MISSILE_TOP_Y, base.z + 1.5);
    }

    @Nullable
    public static BlockPos firstBlocked(Set<BlockPos> cells, Pose pose, Solid solid) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : cells) {
            if (!solid.at(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            double dx = p.getX() + 0.5 - pose.x();
            double dy = p.getY() + 0.5 - pose.y();
            double dz = p.getZ() + 0.5 - pose.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist || (d == bestDist && best != null && p.asLong() < best.asLong())) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Nullable
    public static BlockPos roofBlock(Pose pose, Solid solid) {
        return firstBlocked(blocks(ROOF, pose), pose, solid);
    }

    @Nullable
    public static BlockPos arcBlock(int slot, Pose pose, Solid solid) {
        return firstBlocked(blocks(ARC.get(slot), pose), pose, solid);
    }

    @Nullable
    public static BlockPos skyBlock(int slot, Pose pose, int clearance, Solid solid) {
        return firstBlocked(skyBlocks(slot, pose, clearance), pose, solid);
    }

    public static String describe(@Nullable BlockPos pos) {
        return pos == null ? "" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
