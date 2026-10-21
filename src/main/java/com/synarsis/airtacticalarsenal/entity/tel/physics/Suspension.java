package com.synarsis.airtacticalarsenal.entity.tel.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;

public final class Suspension {

    public static final int WHEELS = 8;
    public static final int SKIDS = 8;
    public static final double MAX_ANGLE = 35.0;
    public static final double SQUASH = 0.15;
    public static final double SKID_SQUASH = 0.05;
    public static final double SKID_MARGIN = 0.02;
    public static final double DROOP = 0.5;
    public static final double VIRTUAL_DROP = 1.0;
    public static final double OMEGA = 2.0 * Math.PI * 1.2;
    public static final double ZETA = 0.5;
    public static final double DT = 0.05;
    public static final double MAX_RISE_PER_TICK = 0.6;
    public static final double RISE_GAIN = 0.45;

    public static double wheelX(int i) {
        return (i & 1) == 0 ? TelGeometry.WHEEL_X : -TelGeometry.WHEEL_X;
    }

    public static double wheelZ(int i) {
        return TelGeometry.WHEEL_Z[i >> 1];
    }

    public static double skidX(int i) {
        return (i & 1) == 0 ? TelGeometry.SKID_X : -TelGeometry.SKID_X;
    }

    public static double skidZ(int i) {
        return TelGeometry.SKID_Z[i >> 1];
    }

    public static double windowTop(double bottom, double climb) {
        return bottom + SQUASH + climb;
    }

    public static final class State {
        public double y;
        public double pitch;
        public double roll;
        public double vy;
        public double pitchRate;
        public double rollRate;
        public boolean airborne;
    }

    public record Input(double[] wheel, boolean[] wall, double[] skid) {
        public static Input wheelsOnly(double[] wheel) {
            double[] none = new double[SKIDS];
            java.util.Arrays.fill(none, Double.NaN);
            return new Input(wheel, null, none);
        }

        boolean isWall(int i) {
            return this.wall != null && this.wall[i];
        }
    }

    public record Target(boolean contact, boolean plane, double y, double pitchDeg, double rollDeg,
            int wheelContacts, int skidSupports, boolean lopsided) {
    }

    public record Result(boolean onGround, boolean contact, double landingSpeed,
            int wheelContacts, int skidSupports) {
    }

    public static double bottom(double y, double pitchDeg, double rollDeg, int i) {
        return point(y, pitchDeg, rollDeg, wheelX(i), 0.0, wheelZ(i));
    }

    public static double skidBottom(double y, double pitchDeg, double rollDeg, int i) {
        return point(y, pitchDeg, rollDeg, skidX(i), TelGeometry.BELLY, skidZ(i));
    }

    private static double point(double y, double pitchDeg, double rollDeg, double lx, double ly, double lz) {
        return TelGeometry.localToWorld(0.0, y, 0.0, 0.0f, (float) pitchDeg, (float) rollDeg,
                new Vec3(lx, ly, lz)).y;
    }

    private static final class Points {
        final double[] x = new double[WHEELS + SKIDS * 2];
        final double[] z = new double[WHEELS + SKIDS * 2];
        final double[] h = new double[WHEELS + SKIDS * 2];
        int n;

        void add(double lx, double lz, double height) {
            this.x[this.n] = lx;
            this.z[this.n] = lz;
            this.h[this.n] = height;
            this.n++;
        }
    }

    private static double[] fit(Points p) {
        if (p.n < 3) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        double mx = 0.0;
        double mz = 0.0;
        double mh = 0.0;
        for (int i = 0; i < p.n; i++) {
            minX = Math.min(minX, p.x[i]);
            maxX = Math.max(maxX, p.x[i]);
            minZ = Math.min(minZ, p.z[i]);
            maxZ = Math.max(maxZ, p.z[i]);
            mx += p.x[i];
            mz += p.z[i];
            mh += p.h[i];
        }
        if (!(minX < -0.1 && maxX > 0.1) || maxZ - minZ < 2.0) {
            return null;
        }
        mx /= p.n;
        mz /= p.n;
        mh /= p.n;
        double sxx = 0.0;
        double szz = 0.0;
        double sxz = 0.0;
        double sxh = 0.0;
        double szh = 0.0;
        for (int i = 0; i < p.n; i++) {
            double dx = p.x[i] - mx;
            double dz = p.z[i] - mz;
            double dh = p.h[i] - mh;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
            sxh += dx * dh;
            szh += dz * dh;
        }
        double det = sxx * szz - sxz * sxz;
        if (!(det > 1.0E-6)) {
            return null;
        }
        double b = (sxh * szz - szh * sxz) / det;
        double c = (szh * sxx - sxh * sxz) / det;
        double a = mh - b * mx - c * mz;
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)) {
            return null;
        }
        return new double[] {a, b, c};
    }

    public static Target target(double y, double pitch, double roll, Input in) {
        Points pts = new Points();
        int wheelContacts = 0;
        double minLz = Double.MAX_VALUE;
        double maxLz = -Double.MAX_VALUE;
        double minLx = Double.MAX_VALUE;
        double maxLx = -Double.MAX_VALUE;
        for (int i = 0; i < WHEELS; i++) {
            if (Double.isNaN(in.wheel()[i])) {
                continue;
            }
            wheelContacts++;
            pts.add(wheelX(i), wheelZ(i), in.wheel()[i] - y);
            minLz = Math.min(minLz, wheelZ(i));
            maxLz = Math.max(maxLz, wheelZ(i));
            minLx = Math.min(minLx, wheelX(i));
            maxLx = Math.max(maxLx, wheelX(i));
        }

        double ty = y;
        double tp = pitch;
        double tr = roll;
        double[] wheelFit = fit(pts);
        if (wheelFit != null) {
            tp = clamp(-Math.toDegrees(Math.atan(wheelFit[2])), -MAX_ANGLE, MAX_ANGLE);
            tr = clamp(Math.toDegrees(Math.atan(wheelFit[1])), -MAX_ANGLE, MAX_ANGLE);
            ty = y + wheelFit[0];
        } else if (wheelContacts > 0) {
            ty = y + highestSink(in, y, pitch, roll, false);
        }

        boolean[] skidCarries = new boolean[SKIDS];
        int skidSupports = 0;
        for (int i = 0; i < SKIDS; i++) {
            double g = in.skid()[i];
            if (Double.isNaN(g)) {
                continue;
            }
            if (g - skidBottom(ty, tp, tr, i) > SKID_MARGIN) {
                skidCarries[i] = true;
                skidSupports++;
                pts.add(skidX(i), skidZ(i), g - TelGeometry.BELLY - y);
                minLz = Math.min(minLz, skidZ(i));
                maxLz = Math.max(maxLz, skidZ(i));
                minLx = Math.min(minLx, skidX(i));
                maxLx = Math.max(maxLx, skidX(i));
            }
        }

        if (wheelContacts + skidSupports == 0) {
            return new Target(false, false, y, pitch, roll, 0, 0, false);
        }

        boolean lopsided = maxLz < TelGeometry.COM_Z || minLz > TelGeometry.COM_Z || maxLx < 0.0 || minLx > 0.0;
        if (lopsided) {
            for (int i = 0; i < WHEELS; i++) {
                if (Double.isNaN(in.wheel()[i]) && !in.isWall(i)) {
                    pts.add(wheelX(i), wheelZ(i), bottom(y, pitch, roll, i) - VIRTUAL_DROP - y);
                }
            }
        }

        double[] plane = fit(pts);
        boolean hasPlane = plane != null;
        if (hasPlane) {
            tp = clamp(-Math.toDegrees(Math.atan(plane[2])), -MAX_ANGLE, MAX_ANGLE);
            tr = clamp(Math.toDegrees(Math.atan(plane[1])), -MAX_ANGLE, MAX_ANGLE);
            ty = y + plane[0];
        } else {
            tp = pitch;
            tr = roll;
            ty = y + highestSink(in, y, pitch, roll, true);
        }

        if (hasPlane) {
            double lift = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < WHEELS; i++) {
                if (!Double.isNaN(in.wheel()[i])) {
                    lift = Math.max(lift, in.wheel()[i] - bottom(ty, tp, tr, i) - SQUASH);
                }
            }
            for (int i = 0; i < SKIDS; i++) {
                if (skidCarries[i]) {
                    lift = Math.max(lift, in.skid()[i] - skidBottom(ty, tp, tr, i) - SKID_SQUASH);
                }
            }
            if (lift > 0.0) {
                ty += lift;
            }
        }
        return new Target(true, hasPlane, ty, tp, tr, wheelContacts, skidSupports, lopsided && hasPlane);
    }

    private static double highestSink(Input in, double y, double pitch, double roll, boolean withSkids) {
        double lift = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < WHEELS; i++) {
            if (!Double.isNaN(in.wheel()[i])) {
                lift = Math.max(lift, in.wheel()[i] - bottom(y, pitch, roll, i));
            }
        }
        if (withSkids) {
            for (int i = 0; i < SKIDS; i++) {
                double g = in.skid()[i];
                if (!Double.isNaN(g) && g - skidBottom(y, pitch, roll, i) > SKID_MARGIN) {
                    lift = Math.max(lift, g - skidBottom(y, pitch, roll, i));
                }
            }
        }
        return lift == Double.NEGATIVE_INFINITY ? 0.0 : lift;
    }

    public static Result step(State s, Input in, boolean inWater, double gravity) {
        Target t = target(s.y, s.pitch, s.roll, in);
        double landing = 0.0;

        if (!t.contact()) {
            s.airborne = true;
            s.vy -= gravity;
            s.y += s.vy;
            if (inWater) {
                s.pitch *= 0.9;
                s.roll *= 0.9;
                s.pitchRate = 0.0;
                s.rollRate = 0.0;
            } else {
                s.pitchRate *= 0.98;
                s.rollRate *= 0.98;
                s.pitch += s.pitchRate * DT;
                s.roll += s.rollRate * DT;
            }
            return new Result(false, false, 0.0, 0, 0);
        }
        if (!Double.isFinite(t.y())) {
            return new Result(false, true, 0.0, t.wheelContacts(), t.skidSupports());
        }

        s.pitchRate += (OMEGA * OMEGA * (t.pitchDeg() - s.pitch) - 2.0 * ZETA * OMEGA * s.pitchRate) * DT;
        s.pitch += s.pitchRate * DT;
        s.rollRate += (OMEGA * OMEGA * (t.rollDeg() - s.roll) - 2.0 * ZETA * OMEGA * s.rollRate) * DT;
        s.roll += s.rollRate * DT;

        double above = s.y - t.y();
        if (!s.airborne && above > 0.02 && above <= DROOP) {
            s.vy = t.y() - s.y;
            s.y = t.y();
        } else if (s.airborne || above > 0.02) {
            s.airborne = true;
            s.vy -= gravity;
            s.y += s.vy;
            if (s.y <= t.y() && s.vy < 0.0) {
                landing = -s.vy;
                s.y = t.y();
                s.vy = s.vy < -0.2 ? -s.vy * 0.15 : 0.0;
                s.airborne = s.vy > 0.0;
            }
        } else if (s.y < t.y() - 0.01) {
            s.vy = clamp((t.y() - s.y) * RISE_GAIN, 0.0, MAX_RISE_PER_TICK);
            s.y += s.vy;
        } else if (s.y < t.y()) {
            s.y = t.y();
            s.vy = 0.0;
        } else {
            s.vy = 0.0;
        }

        boolean onGround = !s.airborne && Math.abs(s.y - t.y()) <= 0.05;
        return new Result(onGround, true, landing, t.wheelContacts(), t.skidSupports());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
