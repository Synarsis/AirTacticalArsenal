package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.ShahedLauncherEntity;
import com.synarsis.airtacticalarsenal.util.LauncherZoneValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record LaunchShahedByRoutePacket(List<BlockPos> launcherPositions, int altitudeMode) implements CustomPacketPayload {

    public LaunchShahedByRoutePacket(BlockPos launcherPos, int altitudeMode) {
        this(List.of(launcherPos), altitudeMode);
    }

    public static final Type<LaunchShahedByRoutePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "launch_shahed_by_route"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchShahedByRoutePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.launcherPositions.size());
                for (BlockPos pos : p.launcherPositions) {
                    buf.writeBlockPos(pos);
                }
                buf.writeInt(p.altitudeMode);
            },
            (buf) -> {
                int n = PacketLimits.readCount(buf, PacketLimits.MAX_LAUNCHERS, "launcher");
                List<BlockPos> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
                int mode = buf.readInt();
                return new LaunchShahedByRoutePacket(list, mode);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaunchShahedByRoutePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            int altitude = payload.altitudeMode();
            int launched = 0;
            int failed = 0;

            for (BlockPos pos : payload.launcherPositions()) {
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
                if (com.synarsis.airtacticalarsenal.debug.DebugOverrides.limitsApply() && playerDistance > 30.0) { failed++; continue; }

                BlockPos firstTarget = route.get(0);
                float launcherYaw = launcher.getYRot();
                if (!LauncherZoneValidator.isTargetInValidZone(launcher.blockPosition(), launcherYaw, firstTarget)) {
                    failed++;
                    continue;
                }

                launcher.startLaunchSequenceWithRoute(player, finalTarget, "low", intermediateWaypoints, altitude);
                launched++;
            }

            if (payload.launcherPositions().size() == 1) {
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
    }

    private static ShahedLauncherEntity findLauncher(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<ShahedLauncherEntity> launchers = level.getEntitiesOfClass(ShahedLauncherEntity.class, searchBox);
        return launchers.isEmpty() ? null : launchers.get(0);
    }
}
