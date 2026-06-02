package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChangeShahedAltitudePacket(int entityId, int altitude) implements CustomPacketPayload {

    public static final Type<ChangeShahedAltitudePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "change_shahed_altitude"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChangeShahedAltitudePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChangeShahedAltitudePacket::entityId,
            ByteBufCodecs.VAR_INT, ChangeShahedAltitudePacket::altitude,
            ChangeShahedAltitudePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChangeShahedAltitudePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player rawPlayer = context.player();
            if (!(rawPlayer instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(payload.entityId());
            if (!(entity instanceof ShahedEntity shahed)) return;

            if (shahed.getOwner() != player) return;

            if (shahed.getFlightPhase() != ShahedEntity.FlightPhase.CRUISING) return;

            shahed.setTargetAltitude(payload.altitude());
        });
    }
}
