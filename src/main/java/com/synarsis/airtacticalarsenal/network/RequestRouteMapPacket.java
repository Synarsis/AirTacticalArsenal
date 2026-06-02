package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.radar.ServerRouteMapScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestRouteMapPacket(BlockPos centerPos) implements CustomPacketPayload {

    public static final Type<RequestRouteMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "request_route_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRouteMapPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestRouteMapPacket::centerPos,
            RequestRouteMapPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestRouteMapPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos centerPos = payload.centerPos();

            ServerRouteMapScanner scanner = ServerRouteMapScanner.getOrCreate(centerPos, level);
            BlockPos actualCenter = scanner.getCenterPos();

            if (scanner.isReady()) {
                byte[] png = scanner.getPngBytes();
                if (png != null) {
                    sendPngToPlayer(player, png, actualCenter);
                }
            } else {
                int queuePos = ServerRouteMapScanner.getQueuePosition(centerPos);
                if (queuePos > 1) {
                    NetworkHandler.sendToPlayer(player,
                            new RouteMapDataPacket(actualCenter, RouteMapDataPacket.STATUS_QUEUED, (float) (queuePos - 1), null));
                } else {
                    NetworkHandler.sendToPlayer(player,
                            new RouteMapDataPacket(actualCenter, RouteMapDataPacket.STATUS_BUILDING, scanner.getProgress(), null));
                }
            }
        });
    }

    private static void sendPngToPlayer(ServerPlayer player, byte[] png, BlockPos regionCenter) {
        int chunkSize = 250000;
        int totalChunks = (png.length + chunkSize - 1) / chunkSize;

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * chunkSize;
            int len = Math.min(chunkSize, png.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(png, offset, chunk, 0, len);
            NetworkHandler.sendToPlayer(player,
                    new RouteMapDataPacket(regionCenter, RouteMapDataPacket.STATUS_DATA, i, totalChunks, chunk));
        }
    }
}
