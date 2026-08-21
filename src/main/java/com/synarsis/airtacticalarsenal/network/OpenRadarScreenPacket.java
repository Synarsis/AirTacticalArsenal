package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenRadarScreenPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenRadarScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "open_radar_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRadarScreenPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenRadarScreenPacket::pos,
            OpenRadarScreenPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRadarScreenPacket payload, IPayloadContext context) {
        // Client-only handler. Payload handlers are registered on both sides, and a dedicated
        // server that loads a class referencing client types aborts mod loading entirely -- the
        // server does not start at all. Guard before touching anything client-side.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.ClientScreens.openRadar(payload.pos()));
    }
}
