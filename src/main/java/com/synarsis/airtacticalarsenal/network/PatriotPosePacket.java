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

public record PatriotPosePacket(int vehicleId, float tractorRoll, float yaw, float pitch, float roll)
        implements CustomPacketPayload {

    public static final Type<PatriotPosePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "patriot_pose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatriotPosePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PatriotPosePacket::vehicleId,
            ByteBufCodecs.FLOAT, PatriotPosePacket::tractorRoll,
            ByteBufCodecs.FLOAT, PatriotPosePacket::yaw,
            ByteBufCodecs.FLOAT, PatriotPosePacket::pitch,
            ByteBufCodecs.FLOAT, PatriotPosePacket::roll,
            PatriotPosePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PatriotPosePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.vehicleId());
            if (entity instanceof PatriotVehicleEntity vehicle && vehicle.getControllingPassenger() == player) {
                vehicle.acceptPose(payload.tractorRoll(), payload.yaw(), payload.pitch(), payload.roll());
            }
        });
    }
}
