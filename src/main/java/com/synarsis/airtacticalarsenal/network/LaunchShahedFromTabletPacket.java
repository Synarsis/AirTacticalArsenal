package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
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

import java.util.List;
import java.util.function.Supplier;

public class LaunchShahedFromTabletPacket {

    private final int launcherId;
    private final BlockPos launcherPos;
    private final int targetX;
    private final int targetY;
    private final int targetZ;
    private final String diveMode;

    public LaunchShahedFromTabletPacket(int launcherId, BlockPos launcherPos, 
                                         int targetX, int targetY, int targetZ, String diveMode) {
        this.launcherId = launcherId;
        this.launcherPos = launcherPos;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.diveMode = diveMode != null ? diveMode : "low";
    }

    public LaunchShahedFromTabletPacket(FriendlyByteBuf buf) {
        this.launcherId = buf.readInt();
        this.launcherPos = buf.readBlockPos();
        this.targetX = buf.readInt();
        this.targetY = buf.readInt();
        this.targetZ = buf.readInt();
        this.diveMode = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(launcherId);
        buf.writeBlockPos(launcherPos);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeUtf(diveMode, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();

            ShahedLauncherEntity launcher = findLauncher(level, launcherId, launcherPos);

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

            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

            double targetDistance = launcher.position().distanceTo(Vec3.atCenterOf(targetPos));
            if (targetDistance < 50) {
                player.displayClientMessage(Component.literal("§cЦель слишком близко! Минимум 50 блоков."), true);
                return;
            }

            float launcherYaw = launcher.getYRot();
            if (!LauncherZoneValidator.isTargetInValidZone(launcher.blockPosition(), launcherYaw, targetPos)) {
                double angle = LauncherZoneValidator.calculateDeviationAngle(
                    Vec3.atCenterOf(launcher.blockPosition()), launcherYaw, Vec3.atCenterOf(targetPos));
                player.displayClientMessage(Component.literal(String.format(
                    "§cЦель вне зоны запуска! Угол: %.0f° (макс: %.0f°)", 
                    angle, LauncherZoneValidator.MAX_DEVIATION_ANGLE)), true);
                return;
            }

            launcher.startLaunchSequence(player, targetPos, diveMode);
        });
        ctx.get().setPacketHandled(true);
    }

    private ShahedLauncherEntity findLauncher(ServerLevel level, int entityId, BlockPos pos) {

        Entity entity = level.getEntity(entityId);
        if (entity instanceof ShahedLauncherEntity launcher) {
            return launcher;
        }

        AABB searchBox = new AABB(pos).inflate(2.0);
        List<ShahedLauncherEntity> launchers = level.getEntitiesOfClass(
            ShahedLauncherEntity.class, searchBox
        );

        if (!launchers.isEmpty()) {
            return launchers.get(0);
        }

        return null;
    }
}
