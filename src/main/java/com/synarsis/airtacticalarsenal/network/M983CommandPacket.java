package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record M983CommandPacket(int vehicleId, byte command) implements CustomPacketPayload {

    public static final Type<M983CommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "m983_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, M983CommandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, M983CommandPacket::vehicleId,
            ByteBufCodecs.BYTE, M983CommandPacket::command,
            M983CommandPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(M983CommandPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof M983TractorEntity tractor) {
                tractor.handleCommand(player, payload.command());
            }
        });
    }
}
