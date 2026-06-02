package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.worldmap.WorldMapData;
import com.synarsis.airtacticalarsenal.worldmap.WorldMapScanner;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

public record RequestWorldMapPacket(int centerX, int centerZ, int radius) implements CustomPacketPayload {
    public static final Type<RequestWorldMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "request_world_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorldMapPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RequestWorldMapPacket::centerX,
            ByteBufCodecs.INT, RequestWorldMapPacket::centerZ,
            ByteBufCodecs.INT, RequestWorldMapPacket::radius,
            RequestWorldMapPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestWorldMapPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player rawPlayer = context.player();
            if (!(rawPlayer instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            WorldMapData mapData = WorldMapData.get(level);
            WorldMapScanner scanner = WorldMapScanner.getInstance();

            Set<Long> coastlinePoints = mapData.getCoastlinePointsInRadius(
                    payload.centerX(), payload.centerZ(), payload.radius());

            WorldMapPacket response = new WorldMapPacket(
                    payload.centerX(), payload.centerZ(), payload.radius(),
                    coastlinePoints,
                    mapData.isScanComplete(),
                    scanner.getProgress(),
                    scanner.getTotalChunks()
            );

            PacketDistributor.sendToPlayer(player, response);
        });
    }
}
