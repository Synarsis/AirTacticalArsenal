package com.synarsis.airtacticalarsenal.mixins;

import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAim;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.VehicleRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {

    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
            + "Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void ata$aimAtTheModel(Entity shooter, Vec3 start, Vec3 end, AABB area,
            Predicate<Entity> filter, double distance, CallbackInfoReturnable<EntityHitResult> cir) {
        if (VehicleRegistry.any(shooter.level())) {
            cir.setReturnValue(TelAim.pick(shooter, start, end, area, filter, distance));
        }
    }
}
