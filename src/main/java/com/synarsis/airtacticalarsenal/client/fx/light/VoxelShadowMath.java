package com.synarsis.airtacticalarsenal.client.fx.light;

public final class VoxelShadowMath {

    @FunctionalInterface
    public interface Cells {
        int opacity(int x, int y, int z);
    }

    public static final int MAX_STEPS = 96;
    public static final double MAX_REACH = 64.0;
    public static final double FREE_NEAR_LIGHT = 0.6;
    public static final double FREE_NEAR_BIG_LIGHT = 1.5;
    public static final double BIG_LIGHT_RADIUS = 30.0;
    public static final double SHUT = 0.02;
    public static final double NORMAL_BIAS = 0.05;

    private VoxelShadowMath() {
    }

    public static double occupancyAt(Cells cells, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= OccupancyGrid.SIZE_X || y >= OccupancyGrid.SIZE_Y || z >= OccupancyGrid.SIZE_Z) {
            return 0.0;
        }
        return cells.opacity(x, y, z) / 255.0;
    }

    public static double freeNearLight(boolean spot, double radius) {
        return !spot && radius >= BIG_LIGHT_RADIUS ? FREE_NEAR_BIG_LIGHT : FREE_NEAR_LIGHT;
    }

    public static double shadowRay(Cells cells, double fx, double fy, double fz, double tx, double ty, double tz) {
        return shadowRay(cells, fx, fy, fz, tx, ty, tz, FREE_NEAR_LIGHT);
    }

    public static double shadowRay(Cells cells, double fx, double fy, double fz, double tx, double ty, double tz,
            double freeNear) {
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double reach = Math.min(len - freeNear, MAX_REACH);
        if (reach <= 0.0) {
            return 1.0;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        if (Math.abs(dx) < 1.0e-6) {
            dx = 1.0e-6;
        }
        if (Math.abs(dy) < 1.0e-6) {
            dy = 1.0e-6;
        }
        if (Math.abs(dz) < 1.0e-6) {
            dz = 1.0e-6;
        }
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        int cz = (int) Math.floor(fz);
        int sx = dx > 0.0 ? 1 : -1;
        int sy = dy > 0.0 ? 1 : -1;
        int sz = dz > 0.0 ? 1 : -1;
        double deltaX = Math.abs(1.0 / dx);
        double deltaY = Math.abs(1.0 / dy);
        double deltaZ = Math.abs(1.0 / dz);
        double maxX = ((sx > 0 ? cx + 1 : cx) - fx) / dx;
        double maxY = ((sy > 0 ? cy + 1 : cy) - fy) / dy;
        double maxZ = ((sz > 0 ? cz + 1 : cz) - fz) / dz;
        double transmittance = 1.0;
        double entry = 0.0;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (entry >= reach) {
                break;
            }
            transmittance *= 1.0 - occupancyAt(cells, cx, cy, cz);
            if (transmittance < SHUT) {
                return 0.0;
            }
            if (maxX < maxY && maxX < maxZ) {
                entry = maxX;
                maxX += deltaX;
                cx += sx;
            } else if (maxY < maxZ) {
                entry = maxY;
                maxY += deltaY;
                cy += sy;
            } else {
                entry = maxZ;
                maxZ += deltaZ;
                cz += sz;
            }
        }
        return transmittance;
    }

    public static double[] snapToAxis(double nx, double ny, double nz) {
        double ax = Math.abs(nx);
        double ay = Math.abs(ny);
        double az = Math.abs(nz);
        if (ax >= ay && ax >= az) {
            return new double[] {nx < 0.0 ? -1.0 : 1.0, 0.0, 0.0};
        }
        if (ay >= az) {
            return new double[] {0.0, ny < 0.0 ? -1.0 : 1.0, 0.0};
        }
        return new double[] {0.0, 0.0, nz < 0.0 ? -1.0 : 1.0};
    }

    public static double blockShadow(Cells cells, double[] p, double[] light, int rays, double offset) {
        return blockShadow(cells, p, light, rays, offset, FREE_NEAR_LIGHT);
    }

    public static double blockShadow(Cells cells, double[] p, double[] light, int rays, double offset, double freeNear) {
        if (rays < 2) {
            return shadowRay(cells, p[0], p[1], p[2], light[0], light[1], light[2], freeNear);
        }
        double dx = light[0] - p[0];
        double dy = light[1] - p[1];
        double dz = light[2] - p[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) {
            return 1.0;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        double ux = 0.0;
        double uy = 1.0;
        double uz = 0.0;
        if (Math.abs(dy) > 0.95) {
            ux = 1.0;
            uy = 0.0;
        }
        double px = dy * uz - dz * uy;
        double py = dz * ux - dx * uz;
        double pz = dx * uy - dy * ux;
        double plen = Math.sqrt(px * px + py * py + pz * pz);
        px = px / plen * offset;
        py = py / plen * offset;
        pz = pz / plen * offset;
        double a = shadowRay(cells, p[0], p[1], p[2], light[0] + px, light[1] + py, light[2] + pz, freeNear);
        double b = shadowRay(cells, p[0], p[1], p[2], light[0] - px, light[1] - py, light[2] - pz, freeNear);
        return 0.5 * (a + b);
    }
}
