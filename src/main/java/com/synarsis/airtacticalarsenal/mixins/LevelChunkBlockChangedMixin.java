package com.synarsis.airtacticalarsenal.mixins;

import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkBlockChangedMixin {

    @Shadow
    @Final
    Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"), require = 0)
    private void ata$tellPhysics(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous != null && !this.level.isClientSide) {
            PhysicsWorlds.blockChanged(this.level, pos, previous, state);
        }
    }
}
