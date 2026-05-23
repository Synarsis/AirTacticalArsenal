package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class IskanderExplosionPacket {
    private final Vec3 explosionPos;

    public IskanderExplosionPacket(Vec3 explosionPos) {
        this.explosionPos = explosionPos;
    }

    public IskanderExplosionPacket(FriendlyByteBuf buf) {
        this.explosionPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(explosionPos.x);
        buf.writeDouble(explosionPos.y);
        buf.writeDouble(explosionPos.z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientIskanderPacketHandler.handleExplosion(explosionPos);
        });
        ctx.get().setPacketHandled(true);
    }

    public Vec3 getExplosionPos() {
        return explosionPos;
    }
}
