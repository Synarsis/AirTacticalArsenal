package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.radar.ServerTerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestRadarMapPacket {
    private final BlockPos radarPos;

    public RequestRadarMapPacket(BlockPos radarPos) {
        this.radarPos = radarPos;
    }

    public RequestRadarMapPacket(FriendlyByteBuf buf) {
        this.radarPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(radarPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();

            ServerTerrainScanner scanner = ServerTerrainScanner.getOrCreate(radarPos, level);

            if (scanner.isReady()) {
                byte[] png = scanner.getPngBytes();
                if (png != null) {
                    sendPngToPlayer(player, png);
                }
            } else {
                int queuePos = ServerTerrainScanner.getQueuePosition(radarPos);
                if (queuePos > 1) {

                    NetworkHandler.sendToPlayer(player,
                            new RadarMapDataPacket(radarPos, RadarMapDataPacket.STATUS_QUEUED, (float)(queuePos - 1), null));
                } else {
                    NetworkHandler.sendToPlayer(player,
                            new RadarMapDataPacket(radarPos, RadarMapDataPacket.STATUS_BUILDING, scanner.getProgress(), null));
                }
            }
        });
        return true;
    }

    private void sendPngToPlayer(ServerPlayer player, byte[] png) {

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
