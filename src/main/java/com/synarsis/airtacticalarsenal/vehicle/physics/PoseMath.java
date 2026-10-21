package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class PoseMath {

    public static final double PIVOT_HEIGHT = 1.0;

    private PoseMath() {
    }

    public record Pose(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg) {
    }

    public static double[][] rotation(double yawDeg, double pitchDeg, double rollDeg) {
        double a = Math.toRadians(-yawDeg);
        double b = Math.toRadians(pitchDeg);
        double c = Math.toRadians(rollDeg);
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        double cb = Math.cos(b);
        double sb = Math.sin(b);
        double cc = Math.cos(c);
        double sc = Math.sin(c);
        return new double[][] {
                {ca * cc + sa * sb * sc, -ca * sc + sa * sb * cc, sa * cb},
                {cb * sc, cb * cc, -sb},
                {-sa * cc + ca * sb * sc, sa * sc + ca * sb * cc, ca * cb},
        };
    }

    public static double[][] rotation(double qx, double qy, double qz, double qw) {
        double xx = qx * qx;
        double yy = qy * qy;
        double zz = qz * qz;
        double xy = qx * qy;
        double xz = qx * qz;
        double yz = qy * qz;
        double wx = qw * qx;
        double wy = qw * qy;
        double wz = qw * qz;
        return new double[][] {
                {1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy)},
                {2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx)},
                {2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)},
        };
    }

    public static double[] quaternion(double yawDeg, double pitchDeg, double rollDeg) {
        return quaternion(rotation(yawDeg, pitchDeg, rollDeg));
    }

    public static double[] quaternion(double[][] m) {
        double trace = m[0][0] + m[1][1] + m[2][2];
        double x;
        double y;
        double z;
        double w;
        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            w = 0.25 * s;
            x = (m[2][1] - m[1][2]) / s;
            y = (m[0][2] - m[2][0]) / s;
            z = (m[1][0] - m[0][1]) / s;
        } else if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) {
            double s = Math.sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0;
            w = (m[2][1] - m[1][2]) / s;
            x = 0.25 * s;
            y = (m[0][1] + m[1][0]) / s;
            z = (m[0][2] + m[2][0]) / s;
        } else if (m[1][1] > m[2][2]) {
            double s = Math.sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0;
            w = (m[0][2] - m[2][0]) / s;
            x = (m[0][1] + m[1][0]) / s;
            y = 0.25 * s;
            z = (m[1][2] + m[2][1]) / s;
        } else {
            double s = Math.sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0;
            w = (m[1][0] - m[0][1]) / s;
            x = (m[0][2] + m[2][0]) / s;
            y = (m[1][2] + m[2][1]) / s;
            z = 0.25 * s;
        }
        return new double[] {x, y, z, w};
    }

    public static double[][] multiply(double[][] a, double[][] b) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return out;
    }

    public static double[][] transpose(double[][] a) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i][j] = a[j][i];
            }
        }
        return out;
    }

    public static double[] apply(double[][] m, double[] v) {
        return new double[] {
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
        };
    }

    public static double[][] articulation(double yawDeg, double pitchDeg, double rollDeg) {
        double alpha = Math.toRadians(-yawDeg);
        double theta = Math.toRadians(pitchDeg);
        double phi = Math.toRadians(rollDeg);
        double ca = Math.cos(alpha);
        double sa = Math.sin(alpha);
        double ct = Math.cos(theta);
        double st = Math.sin(theta);
        double cp = Math.cos(phi);
        double sp = Math.sin(phi);
        return new double[][] {
                {ca * cp, st * sa * cp - ct * sp, ct * sa * cp + st * sp},
                {ca * sp, st * sa * sp + ct * cp, ct * sa * sp - st * cp},
                {-sa, st * ca, ct * ca},
        };
    }

    public static double[] articulationAngles(double[][] m) {
        double alpha = -Math.asin(Math.max(-1.0, Math.min(1.0, m[2][0])));
        double theta = Math.atan2(m[2][1], m[2][2]);
        double phi = Math.atan2(m[1][0], m[0][0]);
        return new double[] {Math.toDegrees(-alpha), Math.toDegrees(theta), Math.toDegrees(phi)};
    }

    public static double[] anglesDegrees(double[][] m) {
        double pitch = Math.asin(Math.max(-1.0, Math.min(1.0, -m[1][2])));
        double a = Math.atan2(m[0][2], m[2][2]);
        double roll = Math.atan2(m[1][0], m[1][1]);
        return new double[] {Math.toDegrees(-a), Math.toDegrees(pitch), Math.toDegrees(roll)};
    }

    public static double[] bodyOrigin(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg) {
        double[][] r = rotation(yawDeg, pitchDeg, rollDeg);
        return new double[] {
                x + r[0][1] * -PIVOT_HEIGHT,
                y + PIVOT_HEIGHT + r[1][1] * -PIVOT_HEIGHT,
                z + r[2][1] * -PIVOT_HEIGHT,
        };
    }

    public static Pose entityPose(double bx, double by, double bz, double qx, double qy, double qz, double qw) {
        double[][] r = rotation(qx, qy, qz, qw);
        double[] angles = anglesDegrees(r);
        return new Pose(
                bx + r[0][1] * PIVOT_HEIGHT,
                by - PIVOT_HEIGHT + r[1][1] * PIVOT_HEIGHT,
                bz + r[2][1] * PIVOT_HEIGHT,
                angles[0], angles[1], angles[2]);
    }

    public static double[] toWorld(double[] bodyOrigin, double[][] r, double lx, double ly, double lz) {
        return new double[] {
                bodyOrigin[0] + r[0][0] * lx + r[0][1] * ly + r[0][2] * lz,
                bodyOrigin[1] + r[1][0] * lx + r[1][1] * ly + r[1][2] * lz,
                bodyOrigin[2] + r[2][0] * lx + r[2][1] * ly + r[2][2] * lz,
        };
    }

    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }
}
