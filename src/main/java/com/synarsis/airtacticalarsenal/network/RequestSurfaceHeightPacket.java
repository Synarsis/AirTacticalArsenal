package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestSurfaceHeightPacket {
    private final int x;
    private final int z;

    public RequestSurfaceHeightPacket(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public RequestSurfaceHeightPacket(FriendlyByteBuf buf) {
        this.x = buf.readInt();
        this.z = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
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
        ctx.get().setPacketHandled(true);
        return true;
    }
}
