package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LevelColumns implements BlockColumns {

    private static final double INSIDE = 1.0E-6;

    private final Level level;
    private final boolean skipSoft;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;
    private boolean chunkLoaded;

    public LevelColumns(Level level, boolean skipSoft) {
        this.level = level;
        this.skipSoft = skipSoft;
    }

    @Override
    public double solidTop(double x, double z, double lowY, double highY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!this.loaded(bx, bz)) {
            return Double.NaN;
        }
        int from = Math.min(Mth.floor(highY), this.level.getMaxBuildHeight() - 1);
        int to = Math.max(Mth.floor(lowY), this.level.getMinBuildHeight());
        double fx = x - bx;
        double fz = z - bz;
        for (int by = from; by >= to; by--) {
            this.pos.set(bx, by, bz);
            BlockState state = this.level.getBlockState(this.pos);
            if (state.isAir() || (this.skipSoft && state.is(TelWorld.BREAKABLE))) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(this.level, this.pos, CollisionContext.empty());
            if (shape.isEmpty()) {
                continue;
            }
            double top = Double.NEGATIVE_INFINITY;
            for (AABB box : shape.toAabbs()) {
                if (fx >= box.minX - INSIDE && fx <= box.maxX + INSIDE && fz >= box.minZ - INSIDE && fz <= box.maxZ + INSIDE) {
                    top = Math.max(top, box.maxY);
                }
            }
            if (top == Double.NEGATIVE_INFINITY) {
                continue;
            }
            double height = by + top;
            if (height > highY) {
                continue;
            }
            return height >= lowY ? height : Double.NaN;
        }
        return Double.NaN;
    }

    private boolean loaded(int bx, int bz) {
        int cx = bx >> 4;
        int cz = bz >> 4;
        if (cx != this.chunkX || cz != this.chunkZ) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.chunkLoaded = this.level.hasChunk(cx, cz);
        }
        return this.chunkLoaded;
    }
}
