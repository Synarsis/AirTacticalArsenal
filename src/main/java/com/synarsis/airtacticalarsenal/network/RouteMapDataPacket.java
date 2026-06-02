package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client route terrain map data. Carries either a build-status update
 * (STATUS_BUILDING / STATUS_QUEUED) or a chunk of the assembled PNG (STATUS_DATA).
 *
 * PORT-NOTE: Mirrors the Forge {@code RouteMapDataPacket}. The two Forge constructors are
 *            preserved here as convenience constructors that delegate to the canonical record.
 */
public record RouteMapDataPacket(BlockPos centerPos, int status, float progress,
                                 int chunkIndex, int totalChunks, byte[] data) implements CustomPacketPayload {

    public static final int STATUS_BUILDING = 0;
    public static final int STATUS_DATA = 1;
    public static final int STATUS_QUEUED = 2;

    public RouteMapDataPacket(BlockPos centerPos, int status, float progress, byte[] data) {
        this(centerPos, status, progress, 0, 0, data);
    }

    public RouteMapDataPacket(BlockPos centerPos, int status, int chunkIndex, int totalChunks, byte[] data) {
        this(centerPos, status, 1.0f, chunkIndex, totalChunks, data);
    }

    public static final Type<RouteMapDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "route_map_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteMapDataPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.centerPos);
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
                BlockPos centerPos = buf.readBlockPos();
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
                return new RouteMapDataPacket(centerPos, status, progress, chunkIndex, totalChunks, data);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteMapDataPacket payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.synarsis.airtacticalarsenal.client.radar.RouteTerrainRenderer.handleServerData(
                        payload.centerPos(), payload.status(), payload.progress(),
                        payload.chunkIndex(), payload.totalChunks(), payload.data()));
    }
}
