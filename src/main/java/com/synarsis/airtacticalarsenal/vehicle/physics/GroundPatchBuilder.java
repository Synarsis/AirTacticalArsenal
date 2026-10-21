package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class GroundPatchBuilder {

    public static final int NODES = 128;
    public static final double STEP = 0.5;
    public static final double BELOW = 8.0;
    public static final double ABOVE = 4.5;
    public static final double SLOPE = 0.6;
    public static final double CLIMBABLE_STEP = 1.2;
    public static final double CONE_RADIUS = 2.0;
    public static final double RECENTRE_DISTANCE = 8.0;
    public static final double RECENTRE_HEIGHT = 4.0;

    private static final int REACH = (int) Math.ceil(CONE_RADIUS / STEP);

    private GroundPatchBuilder() {
    }

    static double alignedOrigin(double centre) {
        return Math.floor((centre - 0.5 * NODES * STEP) / STEP) * STEP + 0.5 * STEP;
    }

    public static GroundPatch build(BlockColumns columns, double centreX, double centreY, double centreZ) {
        double originX = alignedOrigin(centreX);
        double originZ = alignedOrigin(centreZ);
        double low = centreY - BELOW;
        double high = centreY + ABOVE;
        double[] raw = new double[NODES * NODES];
        for (int iz = 0; iz < NODES; iz++) {
            double z = originZ + iz * STEP;
            for (int ix = 0; ix < NODES; ix++) {
                raw[iz * NODES + ix] = columns.solidTop(originX + ix * STEP, z, low, high);
            }
        }
        float[] smoothed = softenSteps(raw);
        return new GroundPatch(originX, originZ, centreY, low, high, NODES, STEP, smoothed, raw);
    }

    static float[] softenSteps(double[] raw) {
        float[] out = new float[NODES * NODES];
        for (int iz = 0; iz < NODES; iz++) {
            for (int ix = 0; ix < NODES; ix++) {
                out[iz * NODES + ix] = softenNode(raw, ix, iz);
            }
        }
        return out;
    }

    static float softenNode(double[] raw, int ix, int iz) {
        double own = raw[iz * NODES + ix];
        if (Double.isNaN(own)) {
            return GroundPatch.HOLE;
        }
        double best = own;
        for (int dz = -REACH; dz <= REACH; dz++) {
            int qz = iz + dz;
            if (qz < 0 || qz >= NODES) {
                continue;
            }
            for (int dx = -REACH; dx <= REACH; dx++) {
                int qx = ix + dx;
                if (qx < 0 || qx >= NODES || (dx == 0 && dz == 0)) {
                    continue;
                }
                double distance = STEP * Math.sqrt(dx * dx + dz * dz);
                if (distance > CONE_RADIUS) {
                    continue;
                }
                double other = raw[qz * NODES + qx];
                if (Double.isNaN(other) || other - own > CLIMBABLE_STEP + 1.0E-6) {
                    continue;
                }
                double lifted = other - SLOPE * distance;
                if (lifted > best) {
                    best = lifted;
                }
            }
        }
        return (float) best;
    }

    private static void soften(GroundPatch patch, int minX, int minZ, int maxX, int maxZ) {
        int x0 = Math.max(0, minX);
        int z0 = Math.max(0, minZ);
        int x1 = Math.min(NODES - 1, maxX);
        int z1 = Math.min(NODES - 1, maxZ);
        double[] raw = patch.raw();
        float[] out = patch.heights();
        for (int iz = z0; iz <= z1; iz++) {
            for (int ix = x0; ix <= x1; ix++) {
                out[iz * NODES + ix] = softenNode(raw, ix, iz);
            }
        }
        patch.markDirty(x0, z0, x1, z1);
    }

    public static int refreshBlock(GroundPatch patch, BlockColumns columns, int blockX, int blockZ) {
        if (!patch.hasRaw()) {
            return 0;
        }
        int x0 = (int) Math.ceil((blockX - patch.originX()) / STEP - 1.0E-9);
        int x1 = (int) Math.ceil((blockX + 1 - patch.originX()) / STEP - 1.0E-9) - 1;
        int z0 = (int) Math.ceil((blockZ - patch.originZ()) / STEP - 1.0E-9);
        int z1 = (int) Math.ceil((blockZ + 1 - patch.originZ()) / STEP - 1.0E-9) - 1;
        x0 = Math.max(x0, 0);
        z0 = Math.max(z0, 0);
        x1 = Math.min(x1, NODES - 1);
        z1 = Math.min(z1, NODES - 1);
        if (x0 > x1 || z0 > z1) {
            return 0;
        }
        double[] raw = patch.raw();
        int reads = 0;
        for (int iz = z0; iz <= z1; iz++) {
            for (int ix = x0; ix <= x1; ix++) {
                raw[iz * NODES + ix] = columns.solidTop(patch.nodeX(ix), patch.nodeZ(iz), patch.low(), patch.high());
                reads++;
            }
        }
        soften(patch, x0 - REACH, z0 - REACH, x1 + REACH, z1 + REACH);
        return reads;
    }

    public static int recentre(GroundPatch patch, BlockColumns columns, double centreX, double centreZ) {
        int shiftX = (int) Math.round((alignedOrigin(centreX) - patch.originX()) / STEP);
        int shiftZ = (int) Math.round((alignedOrigin(centreZ) - patch.originZ()) / STEP);
        if (shiftX == 0 && shiftZ == 0) {
            return 0;
        }
        double[] raw = patch.raw();
        float[] heights = patch.heights();
        int reads = 0;
        if (Math.abs(shiftX) >= NODES || Math.abs(shiftZ) >= NODES) {
            patch.moveOrigin(patch.originX() + shiftX * STEP, patch.originZ() + shiftZ * STEP);
            for (int iz = 0; iz < NODES; iz++) {
                for (int ix = 0; ix < NODES; ix++) {
                    raw[iz * NODES + ix] = columns.solidTop(patch.nodeX(ix), patch.nodeZ(iz), patch.low(), patch.high());
                    reads++;
                }
            }
            soften(patch, 0, 0, NODES - 1, NODES - 1);
            patch.markAllDirty();
            return reads;
        }
        double[] oldRaw = raw.clone();
        float[] oldHeights = heights.clone();
        boolean[] fresh = new boolean[NODES * NODES];
        patch.moveOrigin(patch.originX() + shiftX * STEP, patch.originZ() + shiftZ * STEP);
        for (int iz = 0; iz < NODES; iz++) {
            int sz = iz + shiftZ;
            for (int ix = 0; ix < NODES; ix++) {
                int sx = ix + shiftX;
                int index = iz * NODES + ix;
                if (sx >= 0 && sx < NODES && sz >= 0 && sz < NODES) {
                    raw[index] = oldRaw[sz * NODES + sx];
                    heights[index] = oldHeights[sz * NODES + sx];
                } else {
                    raw[index] = columns.solidTop(patch.nodeX(ix), patch.nodeZ(iz), patch.low(), patch.high());
                    fresh[index] = true;
                    reads++;
                }
            }
        }
        boolean[] stale = spread(fresh);
        for (int iz = 0; iz < NODES; iz++) {
            for (int ix = 0; ix < NODES; ix++) {
                boolean nearDroppedX = shiftX > 0 ? ix < REACH : (shiftX < 0 && ix >= NODES - REACH);
                boolean nearDroppedZ = shiftZ > 0 ? iz < REACH : (shiftZ < 0 && iz >= NODES - REACH);
                if (nearDroppedX || nearDroppedZ) {
                    stale[iz * NODES + ix] = true;
                }
            }
        }
        for (int iz = 0; iz < NODES; iz++) {
            for (int ix = 0; ix < NODES; ix++) {
                if (stale[iz * NODES + ix]) {
                    heights[iz * NODES + ix] = softenNode(raw, ix, iz);
                }
            }
        }
        patch.markAllDirty();
        return reads;
    }

    private static boolean[] spread(boolean[] marked) {
        boolean[] across = new boolean[NODES * NODES];
        for (int iz = 0; iz < NODES; iz++) {
            for (int ix = 0; ix < NODES; ix++) {
                if (!marked[iz * NODES + ix]) {
                    continue;
                }
                int from = Math.max(0, ix - REACH);
                int to = Math.min(NODES - 1, ix + REACH);
                for (int k = from; k <= to; k++) {
                    across[iz * NODES + k] = true;
                }
            }
        }
        boolean[] out = new boolean[NODES * NODES];
        for (int iz = 0; iz < NODES; iz++) {
            for (int ix = 0; ix < NODES; ix++) {
                if (!across[iz * NODES + ix]) {
                    continue;
                }
                int from = Math.max(0, iz - REACH);
                int to = Math.min(NODES - 1, iz + REACH);
                for (int k = from; k <= to; k++) {
                    out[k * NODES + ix] = true;
                }
            }
        }
        return out;
    }

    public static boolean needsRecentre(GroundPatch patch, double x, double z) {
        return Math.abs(x - patch.centreX()) > RECENTRE_DISTANCE || Math.abs(z - patch.centreZ()) > RECENTRE_DISTANCE;
    }

    public static boolean needsNewWindow(GroundPatch patch, double y) {
        return Math.abs(y - patch.centreY()) > RECENTRE_HEIGHT;
    }

    public static boolean needsRebuild(GroundPatch patch, double x, double y, double z, boolean blockChanged) {
        if (patch == null || blockChanged) {
            return true;
        }
        return needsRecentre(patch, x, z) || needsNewWindow(patch, y);
    }

    public static boolean touches(GroundPatch patch, int blockX, int blockZ) {
        return patch != null
                && blockX + 1 > patch.originX() - STEP && blockX < patch.originX() + (patch.count() - 1) * STEP + STEP
                && blockZ + 1 > patch.originZ() - STEP && blockZ < patch.originZ() + (patch.count() - 1) * STEP + STEP;
    }

    public static boolean inWindow(GroundPatch patch, int blockY) {
        return patch != null && blockY + 1 > patch.low() && blockY <= patch.high();
    }
}
