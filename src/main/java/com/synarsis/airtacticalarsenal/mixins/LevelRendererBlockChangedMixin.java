package com.synarsis.airtacticalarsenal.mixins;

import com.synarsis.airtacticalarsenal.client.fx.light.OccupancyVolume;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererBlockChangedMixin {

    @Inject(method = "blockChanged", at = @At("TAIL"), require = 0)
    private void ata$markOccupancy(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags,
            CallbackInfo ci) {
        OccupancyVolume.markChanged(pos);
        if (level instanceof net.minecraft.world.level.Level world) {
            PhysicsWorlds.blockChanged(world, pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
