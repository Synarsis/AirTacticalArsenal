package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UpdateOrlanRoutePacket {

    private final int orlanEntityId;
    private final List<BlockPos> newWaypoints;

    public UpdateOrlanRoutePacket(int orlanEntityId, List<BlockPos> newWaypoints) {
        this.orlanEntityId = orlanEntityId;
        this.newWaypoints = newWaypoints != null ? new ArrayList<>(newWaypoints) : new ArrayList<>();
    }

    public UpdateOrlanRoutePacket(FriendlyByteBuf buf) {
        this.orlanEntityId = buf.readVarInt();
        int count = buf.readVarInt();
        this.newWaypoints = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            this.newWaypoints.add(buf.readBlockPos());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(orlanEntityId);
        buf.writeVarInt(newWaypoints.size());
        for (BlockPos wp : newWaypoints) {
            buf.writeBlockPos(wp);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(orlanEntityId);

            if (!(entity instanceof OrlanEntity orlan)) {
                player.displayClientMessage(Component.literal("§cОрлан не найден!"), true);
                return;
            }

            OrlanEntity.FlightPhase phase = orlan.getFlightPhase();
            if (phase != OrlanEntity.FlightPhase.CRUISING && phase != OrlanEntity.FlightPhase.PATROLLING) {
                player.displayClientMessage(Component.literal("§cОрлан не в полёте! Фаза: " + phase), true);
                return;
            }

            if (newWaypoints.isEmpty()) {
                player.displayClientMessage(Component.literal("§cМаршрут пуст!"), true);
                return;
            }

            BlockPos newTarget = newWaypoints.get(newWaypoints.size() - 1);
            List<BlockPos> intermediateWaypoints = newWaypoints.size() > 1
                    ? new ArrayList<>(newWaypoints.subList(0, newWaypoints.size() - 1))
                    : new ArrayList<>();

            orlan.updateRouteInFlight(newTarget, intermediateWaypoints);

            player.displayClientMessage(
                    Component.literal("§aМаршрут обновлён! " + newWaypoints.size() + " точек"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
