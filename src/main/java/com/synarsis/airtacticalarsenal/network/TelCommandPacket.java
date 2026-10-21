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

public record TelCommandPacket(int vehicleId, byte command, byte mask, int arg) implements CustomPacketPayload {

    public static final Type<TelCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelCommandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelCommandPacket::vehicleId,
            ByteBufCodecs.BYTE, TelCommandPacket::command,
            ByteBufCodecs.BYTE, TelCommandPacket::mask,
            ByteBufCodecs.VAR_INT, TelCommandPacket::arg,
            TelCommandPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelCommandPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof IskanderTelEntity tel) {
                tel.handleCommand(player, payload.command(), payload.mask(), payload.arg());
            }
        });
    }
}
