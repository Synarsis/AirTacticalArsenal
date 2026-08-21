package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record IskanderRemovePacket(int entityId) implements CustomPacketPayload {
    public static final Type<IskanderRemovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "iskander_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IskanderRemovePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IskanderRemovePacket::entityId,
            IskanderRemovePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    public static void handle(IskanderRemovePacket payload, IPayloadContext context) {
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            ClientIskanderPacketHandler.handleIskanderRemove(payload.entityId());
        });
    }
}
