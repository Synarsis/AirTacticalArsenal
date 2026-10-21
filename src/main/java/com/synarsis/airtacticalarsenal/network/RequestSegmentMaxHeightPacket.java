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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestSegmentMaxHeightPacket(int x1, int z1, int x2, int z2, int segmentIndex)
        implements CustomPacketPayload {

    private static final int PROFILE_SAMPLES = 32;

    public static final Type<RequestSegmentMaxHeightPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "request_segment_max_height"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSegmentMaxHeightPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.x1);
                buf.writeInt(p.z1);
                buf.writeInt(p.x2);
                buf.writeInt(p.z2);
                buf.writeVarInt(p.segmentIndex);
            },
            (buf) -> new RequestSegmentMaxHeightPacket(
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestSegmentMaxHeightPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            int[] profile = sampleHeightProfile(level, payload.x1(), payload.z1(), payload.x2(), payload.z2());
            int maxY = 64;
            for (int h : profile) { int abs = Math.abs(h); if (abs > maxY) maxY = abs; }

            NetworkHandler.sendToPlayer(player,
                    new SegmentMaxHeightPacket(payload.x1(), payload.z1(), payload.x2(), payload.z2(),
                            payload.segmentIndex(), maxY, profile));
        });
    }

    private static int[] sampleHeightProfile(ServerLevel level, int ax, int az, int bx, int bz) {
        double dx = bx - ax;
        double dz = bz - az;
        int[] profile = new int[PROFILE_SAMPLES];
        for (int i = 0; i < PROFILE_SAMPLES; i++) {
            double t = (double) i / (PROFILE_SAMPLES - 1);
            int sx = (int) (ax + dx * t);
            int sz = (int) (az + dz * t);
            profile[i] = getTerrainHeight(level, sx, sz);
        }
        return profile;
    }

    private static int getTerrainHeight(ServerLevel level, int x, int z) {

        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (chunk != null) {
            int oceanFloor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
            int worldSurface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);

            if (worldSurface > oceanFloor + 1) return -oceanFloor;
            return oceanFloor;
        }

        try {
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            int oceanFloor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR, level, rs);
            int worldSurface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, level, rs);
            if (worldSurface > oceanFloor + 1) return -oceanFloor;
            return oceanFloor;
        } catch (Exception e) {
            return 64;
        }
    }
}
