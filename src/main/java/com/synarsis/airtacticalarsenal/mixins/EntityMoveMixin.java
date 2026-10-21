package com.synarsis.airtacticalarsenal.mixins;

import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelSolidityHook;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    @Inject(method = "move", at = @At("TAIL"))
    private void ata$keepOutOfTheLauncher(MoverType type, Vec3 movement, CallbackInfo ci) {
        TelSolidityHook.apply((Entity) (Object) this);
    }
}
