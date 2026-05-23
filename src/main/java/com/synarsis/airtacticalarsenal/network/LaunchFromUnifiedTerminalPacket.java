package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LaunchFromUnifiedTerminalPacket {
    private final int targetX;
    private final int targetZ;
    private final BlockPos terminalPos;
    private final List<BlockPos> launcherPositions;
    private final int missileType; 

    public LaunchFromUnifiedTerminalPacket(int targetX, int targetZ, BlockPos terminalPos,
            List<BlockPos> launcherPositions, int missileType) {
        this.targetX = targetX;
        this.targetZ = targetZ;
        this.terminalPos = terminalPos;
        this.launcherPositions = launcherPositions != null ? launcherPositions : new ArrayList<>();
        this.missileType = missileType;
    }

    public LaunchFromUnifiedTerminalPacket(FriendlyByteBuf buf) {
        this.targetX = buf.readInt();
        this.targetZ = buf.readInt();
        this.terminalPos = buf.readBlockPos();
        int listSize = buf.readInt();
        this.launcherPositions = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            this.launcherPositions.add(buf.readBlockPos());
        }
        this.missileType = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetZ);
        buf.writeBlockPos(terminalPos);
        buf.writeInt(launcherPositions.size());
        for (BlockPos pos : launcherPositions) {
            buf.writeBlockPos(pos);
        }
        buf.writeInt(missileType);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
                return;

            ServerLevel level = player.serverLevel();

            Vec3 launchPos;
            BlockPos selectedLauncher = null;

            if (missileType == 1 && !launcherPositions.isEmpty()) {
                for (BlockPos launcherPos : launcherPositions) {
                    BlockState launcherState = level.getBlockState(launcherPos);
                    if (launcherState.getBlock() instanceof LauncherBlock launcherBlock) {
                        if (!launcherState.getValue(LauncherBlock.LOADED)) {
                            if (!launcherBlock.isBlocked(level, launcherPos)) {
                                selectedLauncher = launcherPos;
                                break;
                            }
                        }
                    }
                }

                if (selectedLauncher == null) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.ata.launcher.blocked"), true);
                    return;
                }

                LauncherBlock launcher = (LauncherBlock) level.getBlockState(selectedLauncher).getBlock();
                launcher.setLoaded(level, selectedLauncher, true);
                launchPos = Vec3.atCenterOf(launcher.getLaunchPosition(level, selectedLauncher));
            } else if (!launcherPositions.isEmpty()) {
                BlockPos launcherPos = launcherPositions.get(0);
                BlockState state = level.getBlockState(launcherPos);
                if (state.getBlock() instanceof LauncherBlock launcher) {
                    launchPos = Vec3.atCenterOf(launcher.getLaunchPosition(level, launcherPos));
                } else {
                    launchPos = Vec3.atCenterOf(terminalPos.above(2));
                }
            } else {
                launchPos = Vec3.atCenterOf(terminalPos.above(2));
            }

            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

            BlockPos launchBlockPos = new BlockPos((int) launchPos.x, (int) launchPos.y, (int) launchPos.z);
            if (!level.getFluidState(launchBlockPos).isEmpty()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§cЗапуск невозможен под водой!"), true);
                return;
            }

            if (missileType == 0) {
                ShahedEntity shahed = new ShahedEntity(level, launchPos, targetPos, "low");
                shahed.setOwner(player);
                shahed.isLegitSpawn = true;
                level.addFreshEntity(shahed);
            } else {
                IskanderEntity iskander = new IskanderEntity(level, launchPos, targetPos);
                iskander.setOwner(player);
                if (selectedLauncher != null) {
                    iskander.setLauncherPos(selectedLauncher);
                }
                iskander.isLegitSpawn = true;
                level.addFreshEntity(iskander);
            }
        });
        context.setPacketHandled(true);
    }
}
