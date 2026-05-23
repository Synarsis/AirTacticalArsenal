package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.ShahedLauncherEntity;
import com.synarsis.airtacticalarsenal.util.LauncherZoneValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LaunchShahedByRoutePacket {

    private final List<BlockPos> launcherPositions;
    private final int altitude; 

    public LaunchShahedByRoutePacket(List<BlockPos> launcherPositions, int altitude) {
        this.launcherPositions = new ArrayList<>(launcherPositions);
        this.altitude = altitude;
    }

    public LaunchShahedByRoutePacket(List<BlockPos> launcherPositions) {
        this(launcherPositions, 0);
    }

    public LaunchShahedByRoutePacket(BlockPos singleLauncher, int altitude) {
        this.launcherPositions = new ArrayList<>();
        this.launcherPositions.add(singleLauncher);
        this.altitude = altitude;
    }

    public LaunchShahedByRoutePacket(BlockPos singleLauncher) {
        this(singleLauncher, 0);
    }

    public LaunchShahedByRoutePacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.launcherPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            this.launcherPositions.add(buf.readBlockPos());
        }
        this.altitude = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(launcherPositions.size());
        for (BlockPos pos : launcherPositions) {
            buf.writeBlockPos(pos);
        }
        buf.writeVarInt(altitude);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            int launched = 0;
            int failed = 0;

            for (BlockPos pos : launcherPositions) {
                ShahedLauncherEntity launcher = findLauncher(level, pos);
                if (launcher == null) { failed++; continue; }
                if (!launcher.hasDrone()) { failed++; continue; }
                if (!launcher.hasSavedRoute()) { failed++; continue; }

                List<BlockPos> route = launcher.getSavedRoute();

                BlockPos finalTarget = route.get(route.size() - 1);

                List<BlockPos> intermediateWaypoints = new ArrayList<>();
                if (route.size() > 1) {
                    intermediateWaypoints.addAll(route.subList(0, route.size() - 1));
                }

                double playerDistance = player.position().distanceTo(launcher.position());
                if (playerDistance > 30.0) { failed++; continue; }

                BlockPos firstTarget = route.get(0);
                float launcherYaw = launcher.getYRot();
                if (!LauncherZoneValidator.isTargetInValidZone(launcher.blockPosition(), launcherYaw, firstTarget)) {
                    failed++;
                    continue;
                }

                launcher.startLaunchSequenceWithRoute(player, finalTarget, "low", intermediateWaypoints, altitude);
                launched++;
            }

            if (launcherPositions.size() == 1) {
                if (launched > 0) {
                    player.displayClientMessage(Component.literal("§aЗапуск выполнен!"), true);
                } else {
                    player.displayClientMessage(Component.literal("§cОшибка запуска!"), true);
                }
            } else {
                player.displayClientMessage(Component.literal(
                        String.format("§aЗапущено: %d §7| §cОшибки: %d", launched, failed)), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private ShahedLauncherEntity findLauncher(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<ShahedLauncherEntity> launchers = level.getEntitiesOfClass(
                ShahedLauncherEntity.class, searchBox);
        return launchers.isEmpty() ? null : launchers.get(0);
    }
}
