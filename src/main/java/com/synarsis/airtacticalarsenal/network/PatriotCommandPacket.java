package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PatriotCommandPacket(int vehicleId, byte command) implements CustomPacketPayload {

    public static final Type<PatriotCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "patriot_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatriotCommandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PatriotCommandPacket::vehicleId,
            ByteBufCodecs.BYTE, PatriotCommandPacket::command,
            PatriotCommandPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PatriotCommandPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof PatriotVehicleEntity vehicle
                    && vehicle.distanceToSqr(player) <= PatriotVehicleEntity.PANEL_RANGE * PatriotVehicleEntity.PANEL_RANGE) {
                vehicle.handleCommand(player, payload.command());
            }
        });
    }
}
