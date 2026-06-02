package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.entity.OrlanLauncherEntity;
import com.synarsis.airtacticalarsenal.util.LauncherZoneValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record LaunchOrlanFromTabletPacket(BlockPos launcherPos, int targetX, int targetZ) implements CustomPacketPayload {

    public static final Type<LaunchOrlanFromTabletPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "launch_orlan_from_tablet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchOrlanFromTabletPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaunchOrlanFromTabletPacket::launcherPos,
            ByteBufCodecs.INT, LaunchOrlanFromTabletPacket::targetX,
            ByteBufCodecs.INT, LaunchOrlanFromTabletPacket::targetZ,
            LaunchOrlanFromTabletPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaunchOrlanFromTabletPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            OrlanLauncherEntity launcher = findLauncher(level, payload.launcherPos());

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
            List<BlockPos> waypoints = null;

            if (launcher.hasSavedRoute()) {
                List<BlockPos> route = launcher.getSavedRoute();
                targetPos = route.get(route.size() - 1);
                if (route.size() > 1) {
                    waypoints = new ArrayList<>(route.subList(0, route.size() - 1));
                }
            } else {
                targetPos = new BlockPos(payload.targetX(), 64, payload.targetZ());
            }

            BlockPos firstTarget = (waypoints != null && !waypoints.isEmpty()) ? waypoints.get(0) : targetPos;
            double firstDistance = Math.sqrt(
                    Math.pow(firstTarget.getX() - launcher.blockPosition().getX(), 2) +
                            Math.pow(firstTarget.getZ() - launcher.blockPosition().getZ(), 2));
            if (firstDistance < 600) {
                player.displayClientMessage(Component.literal("§cПервая точка слишком близко! Минимум 600 блоков."), true);
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
    }

    private static OrlanLauncherEntity findLauncher(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<OrlanLauncherEntity> launchers = level.getEntitiesOfClass(OrlanLauncherEntity.class, searchBox);
        return launchers.isEmpty() ? null : launchers.get(0);
    }
}
