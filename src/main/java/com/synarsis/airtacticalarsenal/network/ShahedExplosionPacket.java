package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShahedExplosionPacket {
    private final Vec3 position;

    public ShahedExplosionPacket(Vec3 position) {
        this.position = position;
    }

    public ShahedExplosionPacket(FriendlyByteBuf buf) {
        this.position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(this.position.x);
        buf.writeDouble(this.position.y);
        buf.writeDouble(this.position.z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(this.position)));
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Vec3 position) {
        com.synarsis.airtacticalarsenal.network.ClientPacketHandler.handleExplosion(position);
    }

    public Vec3 getPosition() {
        return this.position;
    }
}
