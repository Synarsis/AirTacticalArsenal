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

public record TelImpactPacket(int vehicleId, byte kind, float speed) implements CustomPacketPayload {

    public static final Type<TelImpactPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelImpactPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelImpactPacket::vehicleId,
            ByteBufCodecs.BYTE, TelImpactPacket::kind,
            ByteBufCodecs.FLOAT, TelImpactPacket::speed,
            TelImpactPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelImpactPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof IskanderTelEntity tel && tel.getControllingPassenger() == player) {
                tel.applyImpact(payload.kind(), payload.speed());
            }
        });
    }
}
