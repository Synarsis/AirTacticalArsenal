package com.synarsis.airtacticalarsenal.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerVehicleMoveMixin {

    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"),
            require = 0, expect = 2)
    private boolean ata$launcherAnswersForItself(ServerLevel level, Entity vehicle, AABB box, Operation<Boolean> original) {
        if (vehicle instanceof IskanderTelEntity) {
            return true;
        }
        return original.call(level, vehicle, box);
    }
}
