package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OrlanCameraForceExitPacket() implements CustomPacketPayload {

    public static final OrlanCameraForceExitPacket INSTANCE = new OrlanCameraForceExitPacket();

    public static final Type<OrlanCameraForceExitPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "orlan_camera_force_exit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrlanCameraForceExitPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrlanCameraForceExitPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.synarsis.airtacticalarsenal.client.OrlanCameraHandler.forceStopCameraLocal();
        });
    }
}
