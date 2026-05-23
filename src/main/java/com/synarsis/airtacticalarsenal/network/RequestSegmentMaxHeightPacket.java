package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestSegmentMaxHeightPacket {
    private final int x1, z1, x2, z2;
    private final int segmentIndex;

    public RequestSegmentMaxHeightPacket(int x1, int z1, int x2, int z2, int segmentIndex) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.segmentIndex = segmentIndex;
    }

    public RequestSegmentMaxHeightPacket(FriendlyByteBuf buf) {
        this.x1 = buf.readInt();
        this.z1 = buf.readInt();
        this.x2 = buf.readInt();
        this.z2 = buf.readInt();
        this.segmentIndex = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x1);
        buf.writeInt(z1);
        buf.writeInt(x2);
        buf.writeInt(z2);
        buf.writeVarInt(segmentIndex);
    }

    private static final int PROFILE_SAMPLES = 32;

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            int[] profile = sampleHeightProfile(level, x1, z1, x2, z2);
            int maxY = 64;
            for (int h : profile) { int abs = Math.abs(h); if (abs > maxY) maxY = abs; }

            NetworkHandler.sendToPlayer(player,
                    new SegmentMaxHeightPacket(x1, z1, x2, z2, segmentIndex, maxY, profile));
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    private int[] sampleHeightProfile(ServerLevel level, int ax, int az, int bx, int bz) {
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

    private int getTerrainHeight(ServerLevel level, int x, int z) {

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
