package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LaunchRocketPacket(BlockPos launcherPos, BlockPos terminalPos, int targetX, int targetZ) implements CustomPacketPayload {

    public static final Type<LaunchRocketPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "launch_rocket"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchRocketPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaunchRocketPacket::launcherPos,
            BlockPos.STREAM_CODEC, LaunchRocketPacket::terminalPos,
            ByteBufCodecs.INT, LaunchRocketPacket::targetX,
            ByteBufCodecs.INT, LaunchRocketPacket::targetZ,
            LaunchRocketPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaunchRocketPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player rawPlayer = context.player();
            if (!(rawPlayer instanceof ServerPlayer player)) return;

            Level level = player.level();
            BlockPos launcherPos = payload.launcherPos();
            int targetX = payload.targetX();
            int targetZ = payload.targetZ();

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

            if (!com.synarsis.airtacticalarsenal.debug.DebugOverrides.noLaunchLimits()) {
                if (dist < minDist) {
                    player.displayClientMessage(Component.translatable("message.ata.rocket.too_close", minDist), true);
                    return;
                }
                if (dist > maxDist) {
                    player.displayClientMessage(Component.translatable("message.ata.rocket.too_far", maxDist), true);
                    return;
                }
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
    }
}
