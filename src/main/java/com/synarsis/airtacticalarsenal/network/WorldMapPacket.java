package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class WorldMapPacket {
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final Set<Long> coastlinePoints;
    private final boolean scanComplete;
    private final int scanProgress;
    private final int totalChunks;

    public WorldMapPacket(int centerX, int centerZ, int radius, Set<Long> coastlinePoints, 
                          boolean scanComplete, int scanProgress, int totalChunks) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.coastlinePoints = coastlinePoints;
        this.scanComplete = scanComplete;
        this.scanProgress = scanProgress;
        this.totalChunks = totalChunks;
    }

    public WorldMapPacket(FriendlyByteBuf buf) {
        this.centerX = buf.readInt();
        this.centerZ = buf.readInt();
        this.radius = buf.readInt();
        this.scanComplete = buf.readBoolean();
        this.scanProgress = buf.readInt();
        this.totalChunks = buf.readInt();

        int size = buf.readInt();
        this.coastlinePoints = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            coastlinePoints.add(buf.readLong());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
        buf.writeInt(radius);
        buf.writeBoolean(scanComplete);
        buf.writeInt(scanProgress);
        buf.writeInt(totalChunks);

        buf.writeInt(coastlinePoints.size());
        for (Long point : coastlinePoints) {
            buf.writeLong(point);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(this));
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(WorldMapPacket packet) {
        com.synarsis.airtacticalarsenal.network.ClientPacketHandler.handleWorldMapPacket(packet);
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getRadius() {
        return radius;
    }

    public Set<Long> getCoastlinePoints() {
        return coastlinePoints;
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public int getScanProgress() {
        return scanProgress;
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
