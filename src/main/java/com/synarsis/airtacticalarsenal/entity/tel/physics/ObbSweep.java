package com.synarsis.airtacticalarsenal.entity.tel.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ObbSweep {

    private static final double SUB_STEP = 0.4;
    private static final int MAX_ITERATIONS = 4;
    public static final double BELLY_GRAZE = 0.08;

    private ObbSweep() {
    }

    public record Params(double tolerance, double noise) {
        public static final Params STANDARD = new Params(0.1, 0.005);
    }

    public record Result(double dx, double dz, double shortX, double shortZ,
            AABB blockerX, AABB blockerZ) {
    }

    public static Result resolve(Obb start, double mx, double mz, List<AABB> boxes, Params p) {
        List<AABB> near = prefilter(start, mx, mz, boxes);
        AABB[] blockerX = new AABB[1];
        AABB[] blockerZ = new AABB[1];
        double dx = mx == 0.0 ? 0.0 : sweepAxis(start, 0, mx, near, p, blockerX);
        Obb moved = start.translated(dx, 0.0, 0.0);
        double dz = mz == 0.0 ? 0.0 : sweepAxis(moved, 2, mz, near, p, blockerZ);
        return new Result(dx, dz,
                Math.max(Math.abs(mx) - Math.abs(dx), 0.0),
                Math.max(Math.abs(mz) - Math.abs(dz), 0.0),
                blockerX[0], blockerZ[0]);
    }

    public static double depth(Obb obb, List<AABB> boxes, Params p) {
        AABB bounds = obb.worldAabb();
        double worst = 0.0;
        for (AABB box : boxes) {
            if (!bounds.intersects(box)) {
                continue;
            }
            Vec3 push = ObbAabbSat.mtvSideways(obb, box, BELLY_GRAZE);
            if (push != null && push.length() >= p.noise()) {
                worst = Math.max(worst, push.length());
            }
        }
        return worst;
    }

    public static boolean turnAllowed(Obb before, Obb after, List<AABB> boxes, Params p, double limit) {
        double now = depth(after, boxes, p);
        if (now <= limit) {
            return true;
        }
        return now <= depth(before, boxes, p) + 1.0E-6;
    }

    public static boolean intersects(Obb obb, List<AABB> boxes, double tolerance) {
        AABB bounds = obb.worldAabb();
        for (AABB box : boxes) {
            if (!bounds.intersects(box)) {
                continue;
            }
            Vec3 push = ObbAabbSat.mtv(obb, box);
            if (push != null && push.length() > tolerance) {
                return true;
            }
        }
        return false;
    }

    private static double sweepAxis(Obb start, int axis, double m, List<AABB> boxes, Params p, AABB[] blocker) {
        int parts = Math.max(1, (int) Math.ceil(Math.abs(m) / SUB_STEP));
        double chunk = m / parts;
        double accepted = 0.0;
        Obb cur = start;
        for (int k = 0; k < parts; k++) {
            double step = shorten(cur, axis, chunk, boxes, p, blocker);
            accepted += step;
            cur = cur.translated(axis == 0 ? step : 0.0, 0.0, axis == 2 ? step : 0.0);
            if (Math.abs(step) < Math.abs(chunk) - 1.0E-9) {
                break;
            }
        }
        return accepted;
    }

    private static double shorten(Obb cur, int axis, double m, List<AABB> boxes, Params p, AABB[] blocker) {
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            Obb test = cur.translated(axis == 0 ? m : 0.0, 0.0, axis == 2 ? m : 0.0);
            AABB testBounds = test.worldAabb();
            double maxCut = 0.0;
            AABB worst = null;
            for (AABB box : boxes) {
                if (!testBounds.intersects(box)) {
                    continue;
                }
                Vec3 push = ObbAabbSat.mtvSideways(test, box, BELLY_GRAZE);
                if (push == null) {
                    continue;
                }
                double len = push.length();
                if (len < p.noise()) {
                    continue;
                }
                double along = axis == 0 ? push.x : push.z;
                if (m * along >= 0.0) {
                    continue;
                }
                double n = Math.abs(along) / len;
                double excess = len - p.tolerance() * (1.0 - n);
                if (excess <= 0.0) {
                    continue;
                }
                if (excess * n > maxCut) {
                    maxCut = excess * n;
                    worst = box;
                }
            }
            if (maxCut < 1.0E-6) {
                return m;
            }
            blocker[0] = worst;
            m = Math.signum(m) * Math.max(0.0, Math.abs(m) - maxCut);
            if (m == 0.0) {
                return 0.0;
            }
        }
        return m;
    }

    private static List<AABB> prefilter(Obb start, double mx, double mz, List<AABB> boxes) {
        AABB area = start.worldAabb().expandTowards(mx, 0.0, mz).inflate(0.25);
        List<AABB> out = new ArrayList<>();
        for (AABB b : boxes) {
            if (b.intersects(area)) {
                out.add(b);
            }
        }
        return out;
    }
}
