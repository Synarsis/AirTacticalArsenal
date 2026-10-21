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

    private GroundPatchBuilder() {
    }

    public static GroundPatch build(BlockColumns columns, double centreX, double centreY, double centreZ) {
        double originX = Math.floor((centreX - 0.5 * NODES * STEP) / STEP) * STEP + 0.5 * STEP;
        double originZ = Math.floor((centreZ - 0.5 * NODES * STEP) / STEP) * STEP + 0.5 * STEP;
        double low = centreY - BELOW;
        double high = centreY + ABOVE;
        double[] blocky = new double[NODES * NODES];
        for (int iz = 0; iz < NODES; iz++) {
            double z = originZ + iz * STEP;
            for (int ix = 0; ix < NODES; ix++) {
                blocky[iz * NODES + ix] = columns.solidTop(originX + ix * STEP, z, low, high);
            }
        }
        float[] smoothed = softenSteps(blocky);
        return new GroundPatch(originX, originZ, centreY, NODES, STEP, smoothed);
    }

    static float[] softenSteps(double[] blocky) {
        int reach = (int) Math.ceil(CONE_RADIUS / STEP);
        float[] out = new float[NODES * NODES];
        for (int iz = 0; iz < NODES; iz++) {
            for (int ix = 0; ix < NODES; ix++) {
                double own = blocky[iz * NODES + ix];
                if (Double.isNaN(own)) {
                    out[iz * NODES + ix] = GroundPatch.HOLE;
                    continue;
                }
                double best = own;
                for (int dz = -reach; dz <= reach; dz++) {
                    int qz = iz + dz;
                    if (qz < 0 || qz >= NODES) {
                        continue;
                    }
                    for (int dx = -reach; dx <= reach; dx++) {
                        int qx = ix + dx;
                        if (qx < 0 || qx >= NODES || (dx == 0 && dz == 0)) {
                            continue;
                        }
                        double distance = STEP * Math.sqrt(dx * dx + dz * dz);
                        if (distance > CONE_RADIUS) {
                            continue;
                        }
                        double other = blocky[qz * NODES + qx];
                        if (Double.isNaN(other) || other - own > CLIMBABLE_STEP + 1.0E-6) {
                            continue;
                        }
                        double lifted = other - SLOPE * distance;
                        if (lifted > best) {
                            best = lifted;
                        }
                    }
                }
                out[iz * NODES + ix] = (float) best;
            }
        }
        return out;
    }

    public static boolean needsRebuild(GroundPatch patch, double x, double y, double z, boolean blockChanged) {
        if (patch == null || blockChanged) {
            return true;
        }
        return Math.abs(x - patch.centreX()) > RECENTRE_DISTANCE
                || Math.abs(z - patch.centreZ()) > RECENTRE_DISTANCE
                || Math.abs(y - patch.centreY()) > RECENTRE_HEIGHT;
    }

    public static boolean touches(GroundPatch patch, int blockX, int blockZ) {
        return patch != null
                && blockX + 1 > patch.originX() - STEP && blockX < patch.originX() + (patch.count() - 1) * STEP + STEP
                && blockZ + 1 > patch.originZ() - STEP && blockZ < patch.originZ() + (patch.count() - 1) * STEP + STEP;
    }
}
