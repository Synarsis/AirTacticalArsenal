package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record LaunchFromUnifiedTerminalPacket(
        int targetX,
        int targetZ,
        BlockPos terminalPos,
        List<BlockPos> launcherPositions,
        int missileType
) implements CustomPacketPayload {

    public static final Type<LaunchFromUnifiedTerminalPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "launch_from_unified_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchFromUnifiedTerminalPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.targetX);
                buf.writeInt(p.targetZ);
                buf.writeBlockPos(p.terminalPos);
                buf.writeInt(p.launcherPositions.size());
                for (BlockPos pos : p.launcherPositions) {
                    buf.writeBlockPos(pos);
                }
                buf.writeInt(p.missileType);
            },
            (buf) -> {
                int targetX = buf.readInt();
                int targetZ = buf.readInt();
                BlockPos terminalPos = buf.readBlockPos();
                int listSize = buf.readInt();
                List<BlockPos> launcherPositions = new ArrayList<>();
                for (int i = 0; i < listSize; i++) {
                    launcherPositions.add(buf.readBlockPos());
                }
                int missileType = buf.readInt();
                return new LaunchFromUnifiedTerminalPacket(targetX, targetZ, terminalPos, launcherPositions, missileType);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaunchFromUnifiedTerminalPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player rawPlayer = context.player();
            if (!(rawPlayer instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();

            Vec3 launchPos;
            BlockPos selectedLauncher = null;

            int targetX = payload.targetX();
            int targetZ = payload.targetZ();
            BlockPos terminalPos = payload.terminalPos();
            List<BlockPos> launcherPositions = payload.launcherPositions();
            int missileType = payload.missileType();

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
                    player.displayClientMessage(Component.translatable("message.ata.launcher.blocked"), true);
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
                player.displayClientMessage(Component.literal("§cЗапуск невозможен под водой!"), true);
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
    }
}
