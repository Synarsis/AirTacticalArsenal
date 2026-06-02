package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OrlanAltitudePacket(int entityId, int direction) implements CustomPacketPayload {

    public static final Type<OrlanAltitudePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "orlan_altitude"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrlanAltitudePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OrlanAltitudePacket::entityId,
            ByteBufCodecs.INT, OrlanAltitudePacket::direction,
            OrlanAltitudePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrlanAltitudePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(payload.entityId());

            if (!(entity instanceof OrlanEntity orlan)) return;
            if (!orlan.isOwnedBy(player)) return;

            double current = orlan.getTargetAltitude();
            double step = 5.0 * payload.direction();
            double newAlt = Math.max(100.0, Math.min(350.0, current + step));
            orlan.setTargetAltitude(newAlt);
        });
    }
}
