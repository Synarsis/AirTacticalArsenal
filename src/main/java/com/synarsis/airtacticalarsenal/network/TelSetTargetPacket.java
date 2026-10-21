package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TelSetTargetPacket(int vehicleId, byte slot, int x, int z) implements CustomPacketPayload {

    public static final Type<TelSetTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_set_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelSetTargetPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelSetTargetPacket::vehicleId,
            ByteBufCodecs.BYTE, TelSetTargetPacket::slot,
            ByteBufCodecs.INT, TelSetTargetPacket::x,
            ByteBufCodecs.INT, TelSetTargetPacket::z,
            TelSetTargetPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelSetTargetPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof IskanderTelEntity tel) {
                tel.handleSetTarget(player, payload.slot(), payload.x(), payload.z());
            }
        });
    }
}
