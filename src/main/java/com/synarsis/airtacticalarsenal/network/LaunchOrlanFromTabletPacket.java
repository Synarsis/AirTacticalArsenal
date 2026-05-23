package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.entity.OrlanLauncherEntity;
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

import java.util.List;
import java.util.function.Supplier;

public class LaunchOrlanFromTabletPacket {

    private final BlockPos launcherPos;
    private final int targetX;
    private final int targetZ;

    public LaunchOrlanFromTabletPacket(BlockPos launcherPos, int targetX, int targetZ) {
        this.launcherPos = launcherPos;
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    public LaunchOrlanFromTabletPacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
        this.targetX = buf.readInt();
        this.targetZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
        buf.writeInt(targetX);
        buf.writeInt(targetZ);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;

            ServerLevel level = player.serverLevel();

            OrlanLauncherEntity launcher = findLauncher(level, launcherPos);

            if (launcher == null) {
                player.displayClientMessage(Component.literal("§cПУ не найдена!"), true);
                return;
            }

            if (!launcher.hasDrone()) {
                player.displayClientMessage(Component.literal("§cНа ПУ нет Орлана!"), true);
                return;
            }

            double playerDistance = player.position().distanceTo(launcher.position());
            if (playerDistance > 30.0) {
                player.displayClientMessage(Component.literal("§cПУ слишком далеко! Макс. 30 блоков"), true);
                return;
            }

            BlockPos targetPos;
            java.util.List<BlockPos> waypoints = null;

            if (launcher.hasSavedRoute()) {
                java.util.List<BlockPos> route = launcher.getSavedRoute();

                targetPos = route.get(route.size() - 1);

                if (route.size() > 1) {
                    waypoints = new java.util.ArrayList<>(route.subList(0, route.size() - 1));
                }
            } else {
                targetPos = new BlockPos(targetX, 64, targetZ);
            }

            BlockPos firstTarget = (waypoints != null && !waypoints.isEmpty()) ? waypoints.get(0) : targetPos;
            double firstDistance = Math.sqrt(
                    Math.pow(firstTarget.getX() - launcher.blockPosition().getX(), 2) +
                            Math.pow(firstTarget.getZ() - launcher.blockPosition().getZ(), 2));
            if (firstDistance < 600) {
                player.displayClientMessage(Component.literal("§cПервая точка слишком близко! Минимум 600 блоков."),
                        true);
                return;
            }

            float launcherYaw = launcher.getYRot();
            if (!LauncherZoneValidator.isTargetInValidZone(launcher.blockPosition(), launcherYaw, firstTarget)) {
                double angle = LauncherZoneValidator.calculateDeviationAngle(
                        Vec3.atCenterOf(launcher.blockPosition()), launcherYaw, Vec3.atCenterOf(firstTarget));
                player.displayClientMessage(Component.literal(String.format(
                        "§cЦель вне зоны запуска! Угол: %.0f° (макс: %.0f°)",
                        angle, LauncherZoneValidator.MAX_DEVIATION_ANGLE)), true);
                return;
            }

            if (!level.getFluidState(launcher.blockPosition()).isEmpty()) {
                player.displayClientMessage(Component.literal("§cЗапуск невозможен под водой!"), true);
                return;
            }

            Vec3 launchPos = launcher.position().add(0, 1.5, 0);
            OrlanEntity orlan = new OrlanEntity(level, launchPos, targetPos, launcherYaw, launcher.blockPosition());
            orlan.setOwner(player);
            orlan.setOwnerUUID(player.getUUID().toString());
            if (waypoints != null && !waypoints.isEmpty()) {
                orlan.setWaypoints(waypoints);
            }
            orlan.isLegitSpawn = true;
            level.addFreshEntity(orlan);

            launcher.removeDrone();

            player.displayClientMessage(
                    Component.literal("§aОрлан-10 запущен! Цель: [" + targetPos.getX() + ", " + targetPos.getZ() + "]"),
                    true);
        });
        ctx.get().setPacketHandled(true);
    }

    private OrlanLauncherEntity findLauncher(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<OrlanLauncherEntity> launchers = level.getEntitiesOfClass(
                OrlanLauncherEntity.class, searchBox);
        if (!launchers.isEmpty()) {
            return launchers.get(0);
        }
        return null;
    }
}
