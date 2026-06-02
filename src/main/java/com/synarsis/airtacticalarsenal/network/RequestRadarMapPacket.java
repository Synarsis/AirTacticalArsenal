package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.worldmap.ServerTerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestRadarMapPacket(BlockPos radarPos) implements CustomPacketPayload {

    public static final Type<RequestRadarMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "request_radar_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRadarMapPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestRadarMapPacket::radarPos,
            RequestRadarMapPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestRadarMapPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos radarPos = payload.radarPos();

            ServerTerrainScanner scanner = ServerTerrainScanner.getOrCreate(radarPos, level);

            if (scanner.isReady()) {
                byte[] png = scanner.getPngBytes();
                if (png != null) {
                    sendPngToPlayer(player, radarPos, png);
                }
            } else {
                int queuePos = ServerTerrainScanner.getQueuePosition(radarPos);
                if (queuePos > 1) {
                    NetworkHandler.sendToPlayer(player,
                            new RadarMapDataPacket(radarPos, RadarMapDataPacket.STATUS_QUEUED, (float) (queuePos - 1), null));
                } else {
                    NetworkHandler.sendToPlayer(player,
                            new RadarMapDataPacket(radarPos, RadarMapDataPacket.STATUS_BUILDING, scanner.getProgress(), null));
                }
            }
        });
    }

    private static void sendPngToPlayer(ServerPlayer player, BlockPos radarPos, byte[] png) {

        int chunkSize = 250000;
        int totalChunks = (png.length + chunkSize - 1) / chunkSize;

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * chunkSize;
            int len = Math.min(chunkSize, png.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(png, offset, chunk, 0, len);
            NetworkHandler.sendToPlayer(player,
                    new RadarMapDataPacket(radarPos, RadarMapDataPacket.STATUS_DATA, i, totalChunks, chunk));
        }
    }
}
