package com.synarsis.airtacticalarsenal.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.HitboxVehicle;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerVehicleMoveMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void ata$holdMissingTrailer(ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this,
                this.player.serverLevel());
        if (this.player.getRootVehicle() instanceof M983TractorEntity tractor
                && tractor.getControllingPassenger() == this.player && tractor.enforceMissingTrailerHold()) {
            this.player.connection.send(new ClientboundMoveVehiclePacket(tractor));
            callback.cancel();
        }
    }

    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"),
            require = 0, expect = 2)
    private boolean ata$launcherAnswersForItself(ServerLevel level, Entity vehicle, AABB box, Operation<Boolean> original) {
        if (vehicle instanceof HitboxVehicle) {
            return true;
        }
        return original.call(level, vehicle, box);
    }
}
