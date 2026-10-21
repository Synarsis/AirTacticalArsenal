package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class GroundPatch {

    public static final float HOLE = Float.MAX_VALUE;

    private final double originX;
    private final double originZ;
    private final double centreY;
    private final float[] heights;
    private final int count;
    private final double step;

    public GroundPatch(double originX, double originZ, double centreY, int count, double step, float[] heights) {
        this.originX = originX;
        this.originZ = originZ;
        this.centreY = centreY;
        this.count = count;
        this.step = step;
        this.heights = heights;
    }

    public double originX() {
        return this.originX;
    }

    public double originZ() {
        return this.originZ;
    }

    public double centreY() {
        return this.centreY;
    }

    public int count() {
        return this.count;
    }

    public double step() {
        return this.step;
    }

    public float[] heights() {
        return this.heights;
    }

    public double centreX() {
        return this.originX + 0.5 * (this.count - 1) * this.step;
    }

    public double centreZ() {
        return this.originZ + 0.5 * (this.count - 1) * this.step;
    }

    public boolean covers(double x, double z) {
        return x >= this.originX && z >= this.originZ
                && x <= this.originX + (this.count - 1) * this.step
                && z <= this.originZ + (this.count - 1) * this.step;
    }

    public float node(int ix, int iz) {
        return this.heights[iz * this.count + ix];
    }

    public double nodeX(int ix) {
        return this.originX + ix * this.step;
    }

    public double nodeZ(int iz) {
        return this.originZ + iz * this.step;
    }

    public double heightAt(double x, double z) {
        double fx = (x - this.originX) / this.step;
        double fz = (z - this.originZ) / this.step;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        if (ix < 0 || iz < 0 || ix >= this.count - 1 || iz >= this.count - 1) {
            return Double.NaN;
        }
        double tx = fx - ix;
        double tz = fz - iz;
        float a = this.node(ix, iz);
        float b = this.node(ix + 1, iz);
        float c = this.node(ix, iz + 1);
        float d = this.node(ix + 1, iz + 1);
        if (a == HOLE || b == HOLE || c == HOLE || d == HOLE) {
            return Double.NaN;
        }
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    public double steepestSlopeDegrees() {
        double steepest = 0.0;
        for (int iz = 0; iz < this.count; iz++) {
            for (int ix = 0; ix < this.count; ix++) {
                float h = this.node(ix, iz);
                if (h == HOLE) {
                    continue;
                }
                if (ix + 1 < this.count && this.node(ix + 1, iz) != HOLE) {
                    steepest = Math.max(steepest, slope(h, this.node(ix + 1, iz)));
                }
                if (iz + 1 < this.count && this.node(ix, iz + 1) != HOLE) {
                    steepest = Math.max(steepest, slope(h, this.node(ix, iz + 1)));
                }
            }
        }
        return steepest;
    }

    private double slope(float a, float b) {
        return Math.toDegrees(Math.atan2(Math.abs(a - b), this.step));
    }
}
