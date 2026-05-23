package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.worldmap.WorldMapData;
import com.synarsis.airtacticalarsenal.worldmap.WorldMapScanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.function.Supplier;

public class RequestWorldMapPacket {
    private final int centerX;
    private final int centerZ;
    private final int radius;

    public RequestWorldMapPacket(int centerX, int centerZ, int radius) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public RequestWorldMapPacket(FriendlyByteBuf buf) {
        this.centerX = buf.readInt();
        this.centerZ = buf.readInt();
        this.radius = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
        buf.writeInt(radius);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            WorldMapData mapData = WorldMapData.get(level);
            WorldMapScanner scanner = WorldMapScanner.getInstance();

            Set<Long> coastlinePoints = mapData.getCoastlinePointsInRadius(centerX, centerZ, radius);

            WorldMapPacket response = new WorldMapPacket(
                centerX, centerZ, radius,
                coastlinePoints,
                mapData.isScanComplete(),
                scanner.getProgress(),
                scanner.getTotalChunks()
            );

            NetworkHandler.sendToPlayer(player, response);
        });
        context.setPacketHandled(true);
    }
}
