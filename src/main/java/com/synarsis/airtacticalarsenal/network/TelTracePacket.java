package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TelTracePacket(boolean enabled) implements CustomPacketPayload {

    public static final Type<TelTracePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_trace"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelTracePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TelTracePacket::enabled,
            TelTracePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelTracePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> TelInput.traceEnabled = payload.enabled());
    }
}
