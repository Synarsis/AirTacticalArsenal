package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class VehicleGround implements AutoCloseable {

    private final PhysicsWorld world;
    private final int slot;
    private final BlockColumns columns;
    private GroundBody body;
    private boolean dirty;
    private int rebuilds;
    private double lastBuildMillis;

    public VehicleGround(PhysicsWorld world, int slot, BlockColumns columns) {
        this.world = world;
        this.slot = slot;
        this.columns = columns;
    }

    public GroundPatch patch() {
        return this.body == null ? null : this.body.patch();
    }

    public int rebuilds() {
        return this.rebuilds;
    }

    public double lastBuildMillis() {
        return this.lastBuildMillis;
    }

    public void markDirty(int blockX, int blockZ) {
        if (GroundPatchBuilder.touches(this.patch(), blockX, blockZ)) {
            this.dirty = true;
        }
    }

    public boolean update(double x, double y, double z) {
        GroundPatch current = this.patch();
        if (!GroundPatchBuilder.needsRebuild(current, x, y, z, this.dirty)) {
            return false;
        }
        long started = System.nanoTime();
        GroundPatch fresh = GroundPatchBuilder.build(this.columns, x, y, z);
        GroundBody replacement = new GroundBody(this.world, this.slot, fresh);
        GroundBody previous = this.body;
        this.body = replacement;
        if (previous != null) {
            previous.close();
        }
        this.dirty = false;
        this.rebuilds++;
        this.lastBuildMillis = (System.nanoTime() - started) / 1.0E6;
        return true;
    }

    public double heightAt(double x, double z) {
        GroundPatch patch = this.patch();
        return patch == null ? Double.NaN : patch.heightAt(x, z);
    }

    @Override
    public void close() {
        if (this.body != null) {
            this.body.close();
            this.body = null;
        }
    }
}
