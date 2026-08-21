package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanLauncherEntity;
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

public record SaveOrlanRoutePacket(BlockPos launcherPos, List<BlockPos> waypoints) implements CustomPacketPayload {

    public static final Type<SaveOrlanRoutePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "save_orlan_route"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveOrlanRoutePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.launcherPos);
                buf.writeInt(p.waypoints.size());
                for (BlockPos wp : p.waypoints) {
                    buf.writeBlockPos(wp);
                }
            },
            (buf) -> {
                BlockPos pos = buf.readBlockPos();
                int n = PacketLimits.readCount(buf, PacketLimits.MAX_WAYPOINTS, "waypoint");
                List<BlockPos> wps = new ArrayList<>(n);
                for (int i = 0; i < n; i++) wps.add(buf.readBlockPos());
                return new SaveOrlanRoutePacket(pos, wps);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveOrlanRoutePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            OrlanLauncherEntity launcher = findLauncher(level, payload.launcherPos());

            if (launcher == null) {
                player.displayClientMessage(Component.literal("§cПУ не найдена!"), true);
                return;
            }

            double playerDistance = player.position().distanceTo(launcher.position());
            if (playerDistance > 30.0) {
                player.displayClientMessage(Component.literal("§cПУ слишком далеко! Макс. 30 блоков"), true);
                return;
            }

            launcher.setSavedRoute(payload.waypoints());

            if (payload.waypoints().isEmpty()) {
                player.displayClientMessage(Component.literal("§eМаршрут очищен"), true);
            } else {
                player.displayClientMessage(Component.literal("§aМаршрут сохранён (" + payload.waypoints().size() + " точек)"), true);
            }
        });
    }

    private static OrlanLauncherEntity findLauncher(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<OrlanLauncherEntity> launchers = level.getEntitiesOfClass(OrlanLauncherEntity.class, searchBox);
        return launchers.isEmpty() ? null : launchers.get(0);
    }
}
