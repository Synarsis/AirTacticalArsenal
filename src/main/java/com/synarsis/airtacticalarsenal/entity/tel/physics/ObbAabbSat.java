package com.synarsis.airtacticalarsenal.entity.tel.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ObbAabbSat {

    private static final double MIN_CROSS_SQR = 1.0E-6;

    private ObbAabbSat() {
    }

    public static Vec3 mtv(Obb obb, AABB box) {
        return solve(obb, box, false, 0.0);
    }

    public static Vec3 mtvSideways(Obb obb, AABB box, double graze) {
        return solve(obb, box, true, graze);
    }

    private static Vec3 solve(Obb obb, AABB box, boolean sideways, double graze) {
        Vec3 c = obb.centre();
        double dx = (box.minX + box.maxX) * 0.5 - c.x;
        double dy = (box.minY + box.maxY) * 0.5 - c.y;
        double dz = (box.minZ + box.maxZ) * 0.5 - c.z;
        double ex = (box.maxX - box.minX) * 0.5;
        double ey = (box.maxY - box.minY) * 0.5;
        double ez = (box.maxZ - box.minZ) * 0.5;

        double best = Double.MAX_VALUE;
        double upOverlap = Double.MAX_VALUE;
        double bx = 0.0;
        double by = 0.0;
        double bz = 0.0;

        for (int k = 0; k < 15; k++) {
            double ax;
            double ay;
            double az;
            if (k < 3) {
                ax = k == 0 ? 1.0 : 0.0;
                ay = k == 1 ? 1.0 : 0.0;
                az = k == 2 ? 1.0 : 0.0;
            } else if (k < 6) {
                Vec3 u = obb.axis(k - 3);
                ax = u.x;
                ay = u.y;
                az = u.z;
            } else {
                Vec3 u = obb.axis((k - 6) / 3);
                int w = (k - 6) % 3;
                if (w == 0) {
                    ax = 0.0;
                    ay = -u.z;
                    az = u.y;
                } else if (w == 1) {
                    ax = u.z;
                    ay = 0.0;
                    az = -u.x;
                } else {
                    ax = -u.y;
                    ay = u.x;
                    az = 0.0;
                }
                double len2 = ax * ax + ay * ay + az * az;
                if (len2 < MIN_CROSS_SQR) {
                    continue;
                }
                double inv = 1.0 / Math.sqrt(len2);
                ax *= inv;
                ay *= inv;
                az *= inv;
            }

            double rObb = 0.0;
            for (int i = 0; i < 3; i++) {
                Vec3 u = obb.axis(i);
                rObb += obb.half(i) * Math.abs(u.x * ax + u.y * ay + u.z * az);
            }
            double rBox = ex * Math.abs(ax) + ey * Math.abs(ay) + ez * Math.abs(az);
            double dist = dx * ax + dy * ay + dz * az;
            double overlap = rObb + rBox - Math.abs(dist);
            if (overlap <= 0.0) {
                return null;
            }
            if (sideways && Math.abs(ay) > 0.7) {
                upOverlap = Math.min(upOverlap, overlap);
                continue;
            }
            if (overlap < best - 1.0E-9) {
                best = overlap;
                double sign = dist >= 0.0 ? -1.0 : 1.0;
                bx = ax * sign;
                by = ay * sign;
                bz = az * sign;
            }
        }
        if (sideways && upOverlap < graze) {
            return null;
        }
        return new Vec3(bx * best, by * best, bz * best);
    }
}
