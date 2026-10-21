package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RadarMapDataPacket(BlockPos radarPos, int status, float progress,
                                 int chunkIndex, int totalChunks, byte[] data) implements CustomPacketPayload {

    public static final int STATUS_BUILDING = 0;
    public static final int STATUS_DATA = 1;
    public static final int STATUS_QUEUED = 2;

    public RadarMapDataPacket(BlockPos radarPos, int status, float progress, byte[] data) {
        this(radarPos, status, progress, 0, 0, data);
    }

    public RadarMapDataPacket(BlockPos radarPos, int status, int chunkIndex, int totalChunks, byte[] data) {
        this(radarPos, status, 1.0f, chunkIndex, totalChunks, data);
    }

    public static final Type<RadarMapDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "radar_map_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMapDataPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.radarPos);
                buf.writeByte(p.status);
                buf.writeFloat(p.progress);
                buf.writeVarInt(p.chunkIndex);
                buf.writeVarInt(p.totalChunks);
                if (p.data != null) {
                    buf.writeVarInt(p.data.length);
                    buf.writeBytes(p.data);
                } else {
                    buf.writeVarInt(0);
                }
            },
            (buf) -> {
                BlockPos radarPos = buf.readBlockPos();
                int status = buf.readByte();
                float progress = buf.readFloat();
                int chunkIndex = buf.readVarInt();
                int totalChunks = buf.readVarInt();
                int len = buf.readVarInt();
                byte[] data;
                if (len > 0) {
                    data = new byte[len];
                    buf.readBytes(data);
                } else {
                    data = null;
                }
                return new RadarMapDataPacket(radarPos, status, progress, chunkIndex, totalChunks, data);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RadarMapDataPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.synarsis.airtacticalarsenal.client.radar.TerrainMapRenderer.handleServerData(
                        payload.radarPos(), payload.status(), payload.progress(),
                        payload.chunkIndex(), payload.totalChunks(), payload.data()));
    }
}
