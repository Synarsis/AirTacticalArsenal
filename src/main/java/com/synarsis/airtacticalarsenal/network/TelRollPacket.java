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

public record TelRollPacket(int vehicleId, float roll) implements CustomPacketPayload {

    public static final Type<TelRollPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_roll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelRollPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelRollPacket::vehicleId,
            ByteBufCodecs.FLOAT, TelRollPacket::roll,
            TelRollPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelRollPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof IskanderTelEntity tel && tel.getControllingPassenger() == player) {
                tel.acceptRoll(payload.roll());
            }
        });
    }
}
