package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RadarMapDataPacket {
    public static final int STATUS_BUILDING = 0;
    public static final int STATUS_DATA = 1;
    public static final int STATUS_QUEUED = 2;

    private final BlockPos radarPos;
    private final int status;
    private final float progress;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] data;

    public RadarMapDataPacket(BlockPos radarPos, int status, float progress, byte[] data) {
        this.radarPos = radarPos;
        this.status = status;
        this.progress = progress;
        this.chunkIndex = 0;
        this.totalChunks = 0;
        this.data = data;
    }

    public RadarMapDataPacket(BlockPos radarPos, int status, int chunkIndex, int totalChunks, byte[] data) {
        this.radarPos = radarPos;
        this.status = status;
        this.progress = 1.0f;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    public RadarMapDataPacket(FriendlyByteBuf buf) {
        this.radarPos = buf.readBlockPos();
        this.status = buf.readByte();
        this.progress = buf.readFloat();
        this.chunkIndex = buf.readVarInt();
        this.totalChunks = buf.readVarInt();
        int len = buf.readVarInt();
        if (len > 0) {
            this.data = new byte[len];
            buf.readBytes(this.data);
        } else {
            this.data = null;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(radarPos);
        buf.writeByte(status);
        buf.writeFloat(progress);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        if (data != null) {
            buf.writeVarInt(data.length);
            buf.writeBytes(data);
        } else {
            buf.writeVarInt(0);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                    handleClient(radarPos, status, progress, chunkIndex, totalChunks, data));
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(BlockPos radarPos, int status, float progress,
                                     int chunkIndex, int totalChunks, byte[] data) {
        com.synarsis.airtacticalarsenal.client.radar.TerrainMapRenderer.handleServerData(
                radarPos, status, progress, chunkIndex, totalChunks, data);
    }
}
