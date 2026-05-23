package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class LaunchShahedFromTerminalPacket {
    private static final Logger LOGGER = LogManager.getLogger("Shahed");
    private final int targetX;
    private final int targetZ;
    private final BlockPos terminalPos;

    public LaunchShahedFromTerminalPacket(int targetX, int targetZ, BlockPos terminalPos) {
        this.targetX = targetX;
        this.targetZ = targetZ;
        this.terminalPos = terminalPos;
    }

    public LaunchShahedFromTerminalPacket(FriendlyByteBuf buf) {
        this.targetX = buf.readInt();
        this.targetZ = buf.readInt();
        this.terminalPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.targetX);
        buf.writeInt(this.targetZ);
        buf.writeBlockPos(this.terminalPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (ShahedConfig.isInForbiddenZone(this.targetX, this.targetZ)) {
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
                BlockPos targetPos = new BlockPos(this.targetX, targetY, this.targetZ);

                if (ShahedConfig.isLoggingEnabled()) {
                    LOGGER.info("[Shahed] Игрок {} запустил дрон", player.getName().getString());
                    LOGGER.info("[Shahed] Терминал: X={}, Y={}, Z={}", terminalPos.getX(), terminalPos.getY(),
                            terminalPos.getZ());
                    LOGGER.info("[Shahed] Спавн: X={}, Y={}, Z={}", spawnX, spawnY, spawnZ);
                    LOGGER.info("[Shahed] Цель: X={}, Y={}, Z={}", this.targetX, targetY, this.targetZ);
                }

                if (player.level() instanceof ServerLevel serverLevel) {
                    preloadChunks(serverLevel, spawnPos, Vec3.atCenterOf(targetPos));
                }

                ShahedEntity shahed = new ShahedEntity(player.level(), spawnPos, targetPos, "high");
                shahed.setOwner(player);
                shahed.isLegitSpawn = true;
                player.level().addFreshEntity(shahed);
                player.sendSystemMessage(Component.literal("§aДрон запущен! Цель: "
                        + this.targetX + ", " + this.targetZ));
            }
        });
        return true;
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
