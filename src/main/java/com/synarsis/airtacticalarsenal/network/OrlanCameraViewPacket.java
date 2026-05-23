package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class OrlanCameraViewPacket {
    private final int orlanEntityId;
    private final boolean connect;

    public static final int DRONE_CHUNK_RADIUS = 5;

    public static final int PATROLLING_CAMERA_CHUNK_RADIUS = 10;

    public OrlanCameraViewPacket(int orlanEntityId, boolean connect) {
        this.orlanEntityId = orlanEntityId;
        this.connect = connect;
    }

    public OrlanCameraViewPacket(FriendlyByteBuf buf) {
        this.orlanEntityId = buf.readInt();
        this.connect = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(orlanEntityId);
        buf.writeBoolean(connect);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(orlanEntityId);

            if (entity instanceof OrlanEntity orlan) {
                if (connect) {
                    orlan.setLinkedPlayer(player.getUUID().toString());

                    player.setNoGravity(true);

                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();

                    player.connection.send(orlan.getAddEntityPacket());
                    List<SynchedEntityData.DataValue<?>> dataValues = orlan.getEntityData().getNonDefaultValues();
                    if (dataValues != null) {
                        player.connection.send(new ClientboundSetEntityDataPacket(orlan.getId(), dataValues));
                    }
                    player.connection.send(new ClientboundTeleportEntityPacket(orlan));

                    int connectRadius = (orlan.getFlightPhase() == OrlanEntity.FlightPhase.PATROLLING)
                            ? PATROLLING_CAMERA_CHUNK_RADIUS : DRONE_CHUNK_RADIUS;
                    sendDroneChunks(orlan, player, level, connectRadius);
                } else {
                    if (orlan.isLinkedToPlayer(player.getUUID())) {

                        Vec3 physPos = orlan.getCameraPhysicsPos();
                        float physFall = orlan.getCameraPlayerFallDistance();
                        orlan.setLinkedPlayer("");
                        player.setNoGravity(false);

                        player.gameMode.getGameModeForPlayer()
                                .updatePlayerAbilities(player.getAbilities());
                        player.onUpdateAbilities();
                        if (physPos != null) {
                            player.connection.teleport(physPos.x, physPos.y, physPos.z,
                                    player.getYRot(), player.getXRot());
                        }
                        player.fallDistance = physFall;

                        sendPlayerChunks(player, level);
                        resyncPlayerEntities(player, level);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendDroneChunks(OrlanEntity orlan, ServerPlayer player, ServerLevel level) {
        sendDroneChunks(orlan, player, level, DRONE_CHUNK_RADIUS);
    }

    public static void sendDroneChunks(OrlanEntity orlan, ServerPlayer player, ServerLevel level, int radius) {
        ChunkPos droneChunk = orlan.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(droneChunk.x, droneChunk.z));

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = droneChunk.x + dx;
                int cz = droneChunk.z + dz;

                ChunkAccess ca = level.getChunk(cx, cz, ChunkStatus.FULL, true);
                if (ca instanceof LevelChunk lc) {
                    player.connection.send(new ClientboundLevelChunkWithLightPacket(
                            lc, level.getLightEngine(), null, null));
                }
            }
        }
    }

    public static void sendDroneChunksDelta(ChunkPos oldCenter, ChunkPos newCenter, ServerPlayer player, ServerLevel level) {
        sendDroneChunksDelta(oldCenter, newCenter, player, level, DRONE_CHUNK_RADIUS);
    }

    public static void sendDroneChunksDelta(ChunkPos oldCenter, ChunkPos newCenter, ServerPlayer player, ServerLevel level, int r) {
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(newCenter.x, newCenter.z));

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = newCenter.x + dx;
                int cz = newCenter.z + dz;

                if (Math.abs(cx - oldCenter.x) <= r && Math.abs(cz - oldCenter.z) <= r) {
                    continue;
                }
                ChunkAccess ca = level.getChunk(cx, cz, ChunkStatus.FULL, true);
                if (ca instanceof LevelChunk lc) {
                    player.connection.send(new ClientboundLevelChunkWithLightPacket(
                            lc, level.getLightEngine(), null, null));
                }
            }
        }
    }

    public static void sendPlayerChunks(ServerPlayer player, ServerLevel level) {
        ChunkPos pc = player.chunkPosition();

        player.connection.send(new ClientboundSetChunkCacheCenterPacket(pc.x, pc.z));

        int viewDist = level.getServer().getPlayerList().getViewDistance();
        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                ChunkAccess ca = level.getChunk(pc.x + dx, pc.z + dz, ChunkStatus.FULL, false);
                if (ca instanceof LevelChunk lc) {
                    player.connection.send(new ClientboundLevelChunkWithLightPacket(
                            lc, level.getLightEngine(), null, null));
                }
            }
        }

        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public static void resyncPlayerEntities(ServerPlayer player, ServerLevel level) {
        int viewDist = level.getServer().getPlayerList().getViewDistance();
        double range = viewDist * 16.0;
        AABB scanBox = new AABB(
                player.getX() - range, player.getY() - 128, player.getZ() - range,
                player.getX() + range, player.getY() + 128, player.getZ() + range
        );

        List<Entity> entities = level.getEntities(player, scanBox, e -> e.isAlive());
        for (Entity e : entities) {
            player.connection.send(e.getAddEntityPacket());
            var dataValues = e.getEntityData().getNonDefaultValues();
            if (dataValues != null) {
                player.connection.send(new ClientboundSetEntityDataPacket(e.getId(), dataValues));
            }
            player.connection.send(new ClientboundTeleportEntityPacket(e));
            if (e.getDeltaMovement().lengthSqr() > 0.001) {
                player.connection.send(new ClientboundSetEntityMotionPacket(e));
            }
        }
    }
}
