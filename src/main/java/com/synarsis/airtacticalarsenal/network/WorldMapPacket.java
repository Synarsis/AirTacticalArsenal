package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

public record WorldMapPacket(
        int centerX,
        int centerZ,
        int radius,
        Set<Long> coastlinePoints,
        boolean scanComplete,
        int scanProgress,
        int totalChunks
) implements CustomPacketPayload {

    public static final Type<WorldMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "world_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldMapPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.centerX);
                buf.writeInt(p.centerZ);
                buf.writeInt(p.radius);
                buf.writeBoolean(p.scanComplete);
                buf.writeInt(p.scanProgress);
                buf.writeInt(p.totalChunks);
                buf.writeInt(p.coastlinePoints.size());
                for (Long point : p.coastlinePoints) {
                    buf.writeLong(point);
                }
            },
            (buf) -> {
                int centerX = buf.readInt();
                int centerZ = buf.readInt();
                int radius = buf.readInt();
                boolean scanComplete = buf.readBoolean();
                int scanProgress = buf.readInt();
                int totalChunks = buf.readInt();
                int size = buf.readInt();
                Set<Long> coastlinePoints = new HashSet<>(size);
                for (int i = 0; i < size; i++) {
                    coastlinePoints.add(buf.readLong());
                }
                return new WorldMapPacket(centerX, centerZ, radius, coastlinePoints,
                        scanComplete, scanProgress, totalChunks);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public int getRadius() { return radius; }
    public Set<Long> getCoastlinePoints() { return coastlinePoints; }
    public boolean isScanComplete() { return scanComplete; }
    public int getScanProgress() { return scanProgress; }
    public int getTotalChunks() { return totalChunks; }

    public static void handle(WorldMapPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleWorldMapPacket(payload);
        });
    }
}
