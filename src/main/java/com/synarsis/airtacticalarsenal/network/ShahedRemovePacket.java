package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShahedRemovePacket(int entityId) implements CustomPacketPayload {
    public static final Type<ShahedRemovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "shahed_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedRemovePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShahedRemovePacket::entityId,
            ShahedRemovePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() { return entityId; }

    public static void handle(ShahedRemovePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleShahedRemove(payload.entityId());
        });
    }
}
