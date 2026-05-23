package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LaunchRocketPacket {
    private final BlockPos launcherPos;
    private final BlockPos terminalPos;
    private final int targetX;
    private final int targetZ;

    public LaunchRocketPacket(BlockPos launcherPos, BlockPos terminalPos, int targetX, int targetZ) {
        this.launcherPos = launcherPos;
        this.terminalPos = terminalPos;
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    public LaunchRocketPacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
        this.terminalPos = buf.readBlockPos();
        this.targetX = buf.readInt();
        this.targetZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
        buf.writeBlockPos(terminalPos);
        buf.writeInt(targetX);
        buf.writeInt(targetZ);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level level = player.level();

            BlockState launcherState = level.getBlockState(launcherPos);
            if (!(launcherState.getBlock() instanceof LauncherBlock launcher)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.invalid_launcher"), true);
                return;
            }

            if (!launcherState.getValue(LauncherBlock.LOADED)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.not_loaded"), true);
                return;
            }

            double dist = Math.sqrt(Math.pow(targetX - launcherPos.getX(), 2) + Math.pow(targetZ - launcherPos.getZ(), 2));
            int minDist = ShahedConfig.getIskanderMinDistance();
            int maxDist = ShahedConfig.getIskanderMaxRange();

            if (dist < minDist) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.too_close", minDist), true);
                return;
            }
            if (dist > maxDist) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.too_far", maxDist), true);
                return;
            }

            if (ShahedConfig.isInForbiddenZone(targetX, targetZ)) {
                player.displayClientMessage(Component.translatable("gui.ata.terminal.error.forbidden"), true);
                return;
            }

            if (launcher.isBlocked(level, launcherPos)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.blocked_above"), true);
                return;
            }

            BlockPos rocketPos = launcherPos.above();
            BlockEntity be = level.getBlockEntity(rocketPos);
            if (!(be instanceof IskanderRocketBlockEntity rocketEntity)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.not_found"), true);
                return;
            }

            if (rocketEntity.isLaunching()) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.already_launching"), true);
                return;
            }

            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
            rocketEntity.startLaunch(targetPos, launcherPos, player);

            player.displayClientMessage(Component.translatable("message.ata.rocket.launch_started"), true);
        });
        context.setPacketHandled(true);
    }
}
