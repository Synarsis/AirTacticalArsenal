package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class GroundPatch {

    public static final float HOLE = Float.MAX_VALUE;

    public record Region(int minX, int minZ, int maxX, int maxZ) {
        public int width() {
            return this.maxX - this.minX + 1;
        }

        public int depth() {
            return this.maxZ - this.minZ + 1;
        }
    }

    private double originX;
    private double originZ;
    private final double centreY;
    private final double low;
    private final double high;
    private final float[] heights;
    private final double[] raw;
    private final int count;
    private final double step;
    private int dirtyMinX = Integer.MAX_VALUE;
    private int dirtyMinZ = Integer.MAX_VALUE;
    private int dirtyMaxX = -1;
    private int dirtyMaxZ = -1;
    private boolean moved;

    public GroundPatch(double originX, double originZ, double centreY, int count, double step, float[] heights) {
        this(originX, originZ, centreY, centreY - GroundPatchBuilder.BELOW, centreY + GroundPatchBuilder.ABOVE, count,
                step, heights, null);
    }

    GroundPatch(double originX, double originZ, double centreY, double low, double high, int count, double step,
            float[] heights, double[] raw) {
        this.originX = originX;
        this.originZ = originZ;
        this.centreY = centreY;
        this.low = low;
        this.high = high;
        this.count = count;
        this.step = step;
        this.heights = heights;
        this.raw = raw;
        this.markAllDirty();
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

    public double low() {
        return this.low;
    }

    public double high() {
        return this.high;
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

    double[] raw() {
        return this.raw;
    }

    boolean hasRaw() {
        return this.raw != null;
    }

    void moveOrigin(double x, double z) {
        this.originX = x;
        this.originZ = z;
        this.moved = true;
    }

    public boolean originMoved() {
        return this.moved;
    }

    public double centreX() {
        return this.originX + 0.5 * (this.count - 1) * this.step;
    }

    public double centreZ() {
        return this.originZ + 0.5 * (this.count - 1) * this.step;
    }

    void markDirty(int minX, int minZ, int maxX, int maxZ) {
        this.dirtyMinX = Math.min(this.dirtyMinX, Math.max(0, minX));
        this.dirtyMinZ = Math.min(this.dirtyMinZ, Math.max(0, minZ));
        this.dirtyMaxX = Math.max(this.dirtyMaxX, Math.min(this.count - 1, maxX));
        this.dirtyMaxZ = Math.max(this.dirtyMaxZ, Math.min(this.count - 1, maxZ));
    }

    void markAllDirty() {
        this.markDirty(0, 0, this.count - 1, this.count - 1);
    }

    public Region takeDirty() {
        if (this.dirtyMaxX < 0) {
            return null;
        }
        Region region = new Region(this.dirtyMinX, this.dirtyMinZ, this.dirtyMaxX, this.dirtyMaxZ);
        this.dirtyMinX = Integer.MAX_VALUE;
        this.dirtyMinZ = Integer.MAX_VALUE;
        this.dirtyMaxX = -1;
        this.dirtyMaxZ = -1;
        this.moved = false;
        return region;
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
