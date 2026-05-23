package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OrlanCameraForceExitPacket {

    public OrlanCameraForceExitPacket() {}

    public OrlanCameraForceExitPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> com.synarsis.airtacticalarsenal.client.OrlanCameraHandler.forceStopCameraLocal()
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
