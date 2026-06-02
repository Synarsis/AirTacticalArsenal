package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
// PORT-NOTE: ChunkStatus moved to net.minecraft.world.level.chunk.status.ChunkStatus in 1.21.1.
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server request for the WORLD_SURFACE height at (x, z). Mirrors the Forge
 * {@code RequestSurfaceHeightPacket}; replies with a {@link SurfaceHeightPacket}.
 */
public record RequestSurfaceHeightPacket(int x, int z) implements CustomPacketPayload {

    public static final Type<RequestSurfaceHeightPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "request_surface_height"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSurfaceHeightPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RequestSurfaceHeightPacket::x,
            ByteBufCodecs.INT, RequestSurfaceHeightPacket::z,
            RequestSurfaceHeightPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestSurfaceHeightPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            int x = payload.x();
            int z = payload.z();
            int surfaceY = 64;

            ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
            if (chunk != null) {
                surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            } else {
                try {
                    surfaceY = level.getChunkSource().getGenerator().getBaseHeight(
                            x, z, Heightmap.Types.WORLD_SURFACE, level, level.getChunkSource().randomState());
                } catch (Exception ignored) {

                }
            }

            NetworkHandler.sendToPlayer(player, new SurfaceHeightPacket(x, z, surfaceY));
        });
    }
}
