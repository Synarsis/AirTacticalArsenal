package com.synarsis.airtacticalarsenal.client.fx.light;

import java.util.List;

public final class EntityShadowMath {

    public static final int MAX_BOXES = 16;
    public static final double MARGIN = 0.15;
    public static final double FREE_NEAR_OWN_LIGHT = 1.5;
    public static final double SHUT = 0.02;
    public static final int NO_OWNER = -1;

    private static final double TINY = 1.0e-6;

    public record Box(double cx, double cy, double cz, double hx, double hy, double hz, double axisX, double axisZ,
            int owner) {

        public double reach() {
            return Math.sqrt(this.hx * this.hx + this.hy * this.hy + this.hz * this.hz) + MARGIN;
        }
    }

    private EntityShadowMath() {
    }

    public static double[] toLocal(Box box, double x, double y, double z) {
        double dx = x - box.cx();
        double dz = z - box.cz();
        return new double[] {dx * box.axisX() + dz * box.axisZ(), y - box.cy(), box.axisX() * dz - box.axisZ() * dx};
    }

    public static double[] slab(double[] o, double[] d, double hx, double hy, double hz) {
        double[] h = {hx, hy, hz};
        double near = 0.0;
        double far = 1.0;
        for (int i = 0; i < 3; i++) {
            double inv = 1.0 / d[i];
            double t0 = (-h[i] - o[i]) * inv;
            double t1 = (h[i] - o[i]) * inv;
            near = Math.max(near, Math.min(t0, t1));
            far = Math.min(far, Math.max(t0, t1));
        }
        return new double[] {near, far};
    }

    private static double guard(double v) {
        return Math.abs(v) < TINY ? TINY : v;
    }

    public static double transmittance(Box box, double[] from, double[] to) {
        double[] a = toLocal(box, from[0], from[1], from[2]);
        double[] b = toLocal(box, to[0], to[1], to[2]);
        double[] d = {guard(b[0] - a[0]), guard(b[1] - a[1]), guard(b[2] - a[2])};
        double[] outer = slab(a, d, box.hx() + MARGIN, box.hy() + MARGIN, box.hz() + MARGIN);
        if (outer[0] > outer[1]) {
            return 1.0;
        }
        double[] inner = slab(a, d, box.hx(), box.hy(), box.hz());
        if (inner[0] <= inner[1]) {
            return 0.0;
        }
        double t = 0.5 * (outer[0] + outer[1]);
        double qx = Math.abs(a[0] + d[0] * t) - box.hx();
        double qy = Math.abs(a[1] + d[1] * t) - box.hy();
        double qz = Math.abs(a[2] + d[2] * t) - box.hz();
        double outside = Math.sqrt(Math.pow(Math.max(qx, 0.0), 2) + Math.pow(Math.max(qy, 0.0), 2)
                + Math.pow(Math.max(qz, 0.0), 2));
        double sdf = outside + Math.min(Math.max(qx, Math.max(qy, qz)), 0.0);
        return LightMath.smoothstep(0.0, MARGIN, sdf);
    }

    public static boolean mayTouch(Box box, double[] from, double[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        double len2 = dx * dx + dy * dy + dz * dz;
        double wx = box.cx() - from[0];
        double wy = box.cy() - from[1];
        double wz = box.cz() - from[2];
        double t = len2 < 1.0e-12 ? 0.0 : LightMath.saturate((wx * dx + wy * dy + wz * dz) / len2);
        double px = from[0] + dx * t - box.cx();
        double py = from[1] + dy * t - box.cy();
        double pz = from[2] + dz * t - box.cz();
        double reach = box.reach();
        return px * px + py * py + pz * pz <= reach * reach;
    }

    public static double shadow(List<Box> boxes, double[] from, double[] to, int lightOwner) {
        double transmitted = 1.0;
        int count = Math.min(boxes.size(), MAX_BOXES);
        for (int i = 0; i < count; i++) {
            Box box = boxes.get(i);
            double[] end = to;
            if (lightOwner != NO_OWNER && box.owner() == lightOwner) {
                end = shortened(from, to, FREE_NEAR_OWN_LIGHT);
            }
            if (!mayTouch(box, from, end)) {
                continue;
            }
            transmitted *= transmittance(box, from, end);
            if (transmitted < SHUT) {
                return 0.0;
            }
        }
        return transmitted;
    }

    public static double[] shortened(double[] from, double[] to, double by) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= by) {
            return from;
        }
        double k = (len - by) / len;
        return new double[] {from[0] + dx * k, from[1] + dy * k, from[2] + dz * k};
    }
}
