package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public record LaunchShahedFromTerminalPacket(int targetX, int targetZ, BlockPos terminalPos) implements CustomPacketPayload {
    private static final Logger LOGGER = AirTacticalArsenal.LOGGER;

    public static final Type<LaunchShahedFromTerminalPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "launch_shahed_from_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchShahedFromTerminalPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LaunchShahedFromTerminalPacket::targetX,
            ByteBufCodecs.INT, LaunchShahedFromTerminalPacket::targetZ,
            BlockPos.STREAM_CODEC, LaunchShahedFromTerminalPacket::terminalPos,
            LaunchShahedFromTerminalPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaunchShahedFromTerminalPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player rawPlayer = context.player();
            if (!(rawPlayer instanceof ServerPlayer player)) return;

            int targetX = payload.targetX();
            int targetZ = payload.targetZ();
            BlockPos terminalPos = payload.terminalPos();

            if (ShahedConfig.isInForbiddenZone(targetX, targetZ)) {
                player.sendSystemMessage(Component.literal("§cНевозможно атаковать запретную зону!"));
                return;
            }
            if (!player.level().getFluidState(terminalPos).isEmpty()) {
                player.sendSystemMessage(Component.literal("§cЗапуск невозможен под водой!"));
                return;
            }
            int spawnX = ShahedConfig.SPAWN_X.get();
            int spawnY = ShahedConfig.SPAWN_Y.get();
            int spawnZ = ShahedConfig.SPAWN_Z.get();
            Vec3 spawnPos = new Vec3(spawnX, spawnY, spawnZ);
            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

            if (ShahedConfig.isLoggingEnabled()) {
                LOGGER.info("[Shahed] Игрок {} запустил дрон", player.getName().getString());
                LOGGER.info("[Shahed] Терминал: X={}, Y={}, Z={}", terminalPos.getX(), terminalPos.getY(),
                        terminalPos.getZ());
                LOGGER.info("[Shahed] Спавн: X={}, Y={}, Z={}", spawnX, spawnY, spawnZ);
                LOGGER.info("[Shahed] Цель: X={}, Y={}, Z={}", targetX, targetY, targetZ);
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                preloadChunks(serverLevel, spawnPos, Vec3.atCenterOf(targetPos));
            }

            ShahedEntity shahed = new ShahedEntity(player.level(), spawnPos, targetPos, "high");
            shahed.setOwner(player);
            shahed.isLegitSpawn = true;
            player.level().addFreshEntity(shahed);
            player.sendSystemMessage(Component.literal("§aДрон запущен! Цель: "
                    + targetX + ", " + targetZ));
        });
    }

    private static void preloadChunks(ServerLevel level, Vec3 from, Vec3 to) {
        ChunkPos spawnChunk = new ChunkPos(new BlockPos((int) from.x, (int) from.y, (int) from.z));
        for (int x = -3; x <= 3; ++x) {
            for (int z = -3; z <= 3; ++z) {
                level.setChunkForced(spawnChunk.x + x, spawnChunk.z + z, true);
            }
        }

        double totalDistance = from.distanceTo(to);
        int numPoints = Math.max(1, (int) (totalDistance / 32.0));
        for (int i = 0; i <= numPoints; ++i) {
            double progress = (double) i / (double) numPoints;
            Vec3 pointOnPath = new Vec3(
                    from.x + (to.x - from.x) * progress,
                    from.y + (to.y - from.y) * progress,
                    from.z + (to.z - from.z) * progress);
            ChunkPos pathChunk = new ChunkPos(
                    new BlockPos((int) pointOnPath.x, (int) pointOnPath.y, (int) pointOnPath.z));
            for (int x = -2; x <= 2; ++x) {
                for (int z = -2; z <= 2; ++z) {
                    level.setChunkForced(pathChunk.x + x, pathChunk.z + z, true);
                }
            }
        }
    }
}
