package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SurfaceHeightPacket(int x, int z, int y) implements CustomPacketPayload {

    public static final Type<SurfaceHeightPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "surface_height"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceHeightPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SurfaceHeightPacket::x,
            ByteBufCodecs.INT, SurfaceHeightPacket::z,
            ByteBufCodecs.INT, SurfaceHeightPacket::y,
            SurfaceHeightPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SurfaceHeightPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.synarsis.airtacticalarsenal.client.gui.SurfaceHeightCache.updateHeight(
                        payload.x(), payload.z(), payload.y()));
    }
}
