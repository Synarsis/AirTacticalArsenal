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

public record PatriotImpactPacket(int vehicleId, byte kind, float speed) implements CustomPacketPayload {

    public static final Type<PatriotImpactPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "patriot_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatriotImpactPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PatriotImpactPacket::vehicleId,
            ByteBufCodecs.BYTE, PatriotImpactPacket::kind,
            ByteBufCodecs.FLOAT, PatriotImpactPacket::speed,
            PatriotImpactPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PatriotImpactPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof PatriotVehicleEntity vehicle && vehicle.getControllingPassenger() == player) {
                vehicle.applyImpact(payload.kind(), payload.speed());
            }
        });
    }
}
