package com.synarsis.airtacticalarsenal.network;

import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VehicleSpecPacket(String json) implements CustomPacketPayload {

    public static final Type<VehicleSpecPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "vehicle_spec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleSpecPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), VehicleSpecPacket::json,
            VehicleSpecPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleSpecPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                VehicleSpecs.apply(payload.json());
            } catch (JsonParseException error) {
                LogUtils.getLogger().warn("The server sent vehicle settings that could not be read: {}", error.getMessage());
            }
        });
    }
}
