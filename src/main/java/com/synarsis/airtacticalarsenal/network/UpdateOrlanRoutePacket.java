package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record UpdateOrlanRoutePacket(int orlanEntityId, List<BlockPos> waypoints) implements CustomPacketPayload {

    public static final Type<UpdateOrlanRoutePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "update_orlan_route"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateOrlanRoutePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.orlanEntityId);
                buf.writeInt(p.waypoints.size());
                for (BlockPos wp : p.waypoints) {
                    buf.writeBlockPos(wp);
                }
            },
            (buf) -> {
                int id = buf.readInt();
                int n = buf.readInt();
                List<BlockPos> wps = new ArrayList<>(n);
                for (int i = 0; i < n; i++) wps.add(buf.readBlockPos());
                return new UpdateOrlanRoutePacket(id, wps);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateOrlanRoutePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(payload.orlanEntityId());

            if (!(entity instanceof OrlanEntity orlan)) {
                player.displayClientMessage(Component.literal("§cОрлан не найден!"), true);
                return;
            }

            OrlanEntity.FlightPhase phase = orlan.getFlightPhase();
            if (phase != OrlanEntity.FlightPhase.CRUISING && phase != OrlanEntity.FlightPhase.PATROLLING) {
                player.displayClientMessage(Component.literal("§cОрлан не в полёте! Фаза: " + phase), true);
                return;
            }

            if (payload.waypoints().isEmpty()) {
                player.displayClientMessage(Component.literal("§cМаршрут пуст!"), true);
                return;
            }

            List<BlockPos> wps = payload.waypoints();
            BlockPos newTarget = wps.get(wps.size() - 1);
            List<BlockPos> intermediateWaypoints = wps.size() > 1
                    ? new ArrayList<>(wps.subList(0, wps.size() - 1))
                    : new ArrayList<>();

            orlan.updateRouteInFlight(newTarget, intermediateWaypoints);

            player.displayClientMessage(
                    Component.literal("§aМаршрут обновлён! " + wps.size() + " точек"), true);
        });
    }
}
