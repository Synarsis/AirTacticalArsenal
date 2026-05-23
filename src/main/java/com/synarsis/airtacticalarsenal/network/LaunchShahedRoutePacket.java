package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.ShahedLauncherEntity;
import com.synarsis.airtacticalarsenal.util.LauncherZoneValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LaunchShahedRoutePacket {

    private final BlockPos launcherPos;
    private final BlockPos finalTarget;
    private final String diveMode;
    private final List<BlockPos> waypoints;

    public LaunchShahedRoutePacket(BlockPos launcherPos, BlockPos finalTarget, String diveMode, List<BlockPos> waypoints) {
        this.launcherPos = launcherPos;
        this.finalTarget = finalTarget;
        this.diveMode = diveMode != null ? diveMode : "low";
        this.waypoints = waypoints != null ? new ArrayList<>(waypoints) : new ArrayList<>();
    }

    public LaunchShahedRoutePacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
        this.finalTarget = buf.readBlockPos();
        this.diveMode = buf.readUtf(32);
        int count = buf.readInt();
        this.waypoints = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            this.waypoints.add(buf.readBlockPos());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
        buf.writeBlockPos(finalTarget);
        buf.writeUtf(diveMode, 32);
        buf.writeInt(waypoints.size());
        for (BlockPos wp : waypoints) {
            buf.writeBlockPos(wp);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();

            ShahedLauncherEntity launcher = findLauncher(level, launcherPos);

            if (launcher == null) {
                player.displayClientMessage(Component.literal("§cПУ не найдена!"), true);
                return;
            }

            if (!launcher.hasDrone()) {
                player.displayClientMessage(Component.literal("§cНа ПУ нет дрона!"), true);
                return;
            }

            double playerDistance = player.position().distanceTo(launcher.position());
            if (playerDistance > 100.0) {
                player.displayClientMessage(Component.literal("§cПУ слишком далеко!"), true);
                return;
            }

            double targetDistance = launcher.position().distanceTo(Vec3.atCenterOf(finalTarget));
            if (targetDistance < 50) {
                player.displayClientMessage(Component.literal("§cЦель слишком близко! Минимум 50 блоков."), true);
                return;
            }

            BlockPos firstTarget = !waypoints.isEmpty() ? waypoints.get(0) : finalTarget;
            float launcherYaw = launcher.getYRot();
            if (!LauncherZoneValidator.isTargetInValidZone(launcher.blockPosition(), launcherYaw, firstTarget)) {
                double angle = LauncherZoneValidator.calculateDeviationAngle(
                    Vec3.atCenterOf(launcher.blockPosition()), launcherYaw, Vec3.atCenterOf(firstTarget));
                player.displayClientMessage(Component.literal(String.format(
                    "§cПервая точка маршрута вне зоны! Угол: %.0f° (макс: %.0f°)", 
                    angle, LauncherZoneValidator.MAX_DEVIATION_ANGLE)), true);
                return;
            }

            launcher.startLaunchSequenceWithRoute(player, finalTarget, diveMode, waypoints);
        });
        ctx.get().setPacketHandled(true);
    }

    private ShahedLauncherEntity findLauncher(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<ShahedLauncherEntity> launchers = level.getEntitiesOfClass(
            ShahedLauncherEntity.class, searchBox
        );
        return launchers.isEmpty() ? null : launchers.get(0);
    }
}
