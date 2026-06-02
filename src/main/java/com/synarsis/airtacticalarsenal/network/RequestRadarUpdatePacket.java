package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record RequestRadarUpdatePacket(BlockPos radarPos) implements CustomPacketPayload {
    public static final Type<RequestRadarUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "request_radar_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRadarUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestRadarUpdatePacket::radarPos,
            RequestRadarUpdatePacket::new
    );

    private static final int RADAR_RANGE = 1500;
    private static final double RADAR_MIN_ALTITUDE = 90.0;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestRadarUpdatePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ServerLevel level = serverPlayer.serverLevel();
            BlockPos radarPos = payload.radarPos();
            Vec3 radarCenter = Vec3.atCenterOf(radarPos);

            AABB searchBox = new AABB(
                    radarCenter.x - RADAR_RANGE, radarCenter.y - 64, radarCenter.z - RADAR_RANGE,
                    radarCenter.x + RADAR_RANGE, radarCenter.y + 350, radarCenter.z + RADAR_RANGE
            );

            double rangeSquared = (double) RADAR_RANGE * RADAR_RANGE;

            List<RadarUpdatePacket.TargetData> targets = new ArrayList<>();
            for (Entity entity : level.getEntities(null, searchBox)) {
                byte type = -1;
                if (entity instanceof ShahedEntity) type = 0;
                else if (entity instanceof IskanderEntity) type = 1;
                else if (entity instanceof OrlanEntity) type = 2;
                else {
                    byte swType = SuperbWarfareCompat.getRadarType(entity);
                    if (swType >= 0) type = swType;
                }
                if (type < 0) continue;

                double dx = entity.getX() - radarCenter.x;
                double dz = entity.getZ() - radarCenter.z;
                if (dx * dx + dz * dz > rangeSquared) continue;

                if (type != 1 && entity.getY() < RADAR_MIN_ALTITUDE) continue;

                Vec3 delta = entity.getDeltaMovement();
                float speed = (float) Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                targets.add(new RadarUpdatePacket.TargetData(
                        entity.getId(), type, entity.position(), entity.getYRot(), speed
                ));
            }

            NetworkHandler.sendToPlayer(serverPlayer, new RadarUpdatePacket(radarPos, targets));
        });
    }
}
