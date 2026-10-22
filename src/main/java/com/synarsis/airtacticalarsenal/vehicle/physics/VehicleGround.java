package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Jolt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VehicleGround implements AutoCloseable {

    public static final int BATCH_LIMIT = 48;

    private final PhysicsWorld world;
    private final int slot;
    private final BlockColumns columns;
    private final Set<Long> pending = new LinkedHashSet<>();
    private GroundBody body;
    private int rebuilds;
    private int fullRebuilds;
    private double lastBuildMillis;

    public VehicleGround(PhysicsWorld world, int slot, BlockColumns columns) {
        this.world = world;
        this.slot = slot;
        this.columns = columns;
    }

    public GroundPatch patch() {
        return this.body == null ? null : this.body.patch();
    }

    public int bodyId() {
        return this.body == null ? Jolt.cInvalidBodyId : this.body.bodyId();
    }

    public int rebuilds() {
        return this.rebuilds;
    }

    public int fullRebuilds() {
        return this.fullRebuilds;
    }

    public double lastBuildMillis() {
        return this.lastBuildMillis;
    }

    public boolean markChanged(int blockX, int blockY, int blockZ) {
        GroundPatch patch = this.patch();
        if (!GroundPatchBuilder.touches(patch, blockX, blockZ) || !GroundPatchBuilder.inWindow(patch, blockY)) {
            return false;
        }
        this.pending.add(key(blockX, blockZ));
        return true;
    }

    public boolean update(double x, double y, double z) {
        GroundPatch current = this.patch();
        long started = System.nanoTime();
        boolean changed;
        if (current == null || GroundPatchBuilder.needsNewWindow(current, y) || this.pending.size() > BATCH_LIMIT) {
            this.rebuildFully(x, y, z);
            changed = true;
        } else {
            changed = this.refresh(current, x, z);
        }
        if (changed) {
            this.rebuilds++;
            this.lastBuildMillis = (System.nanoTime() - started) / 1.0E6;
        }
        return changed;
    }

    private void rebuildFully(double x, double y, double z) {
        GroundPatch fresh = GroundPatchBuilder.build(this.columns, x, y, z);
        GroundBody replacement = new GroundBody(this.world, this.slot, fresh);
        GroundBody previous = this.body;
        this.body = replacement;
        if (previous != null) {
            previous.close();
        }
        this.pending.clear();
        this.fullRebuilds++;
    }

    private boolean refresh(GroundPatch patch, double x, double z) {
        boolean changed = false;
        if (GroundPatchBuilder.needsRecentre(patch, x, z)) {
            GroundPatchBuilder.recentre(patch, this.columns, x, z);
            changed = true;
        }
        if (!this.pending.isEmpty()) {
            List<Long> blocks = new ArrayList<>(this.pending);
            this.pending.clear();
            for (long block : blocks) {
                GroundPatchBuilder.refreshBlock(patch, this.columns, blockX(block), blockZ(block));
            }
            changed = true;
        }
        if (changed) {
            this.body.pushChanges();
        }
        return changed;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int blockX(long key) {
        return (int) (key >> 32);
    }

    private static int blockZ(long key) {
        return (int) key;
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
