package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OrlanRemovePacket(int entityId) implements CustomPacketPayload {
    public static final Type<OrlanRemovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "orlan_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrlanRemovePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OrlanRemovePacket::entityId,
            OrlanRemovePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrlanRemovePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleOrlanRemove(payload.entityId());
        });
    }
}
