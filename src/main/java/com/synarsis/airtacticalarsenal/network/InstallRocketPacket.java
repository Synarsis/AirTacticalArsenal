package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.block.IskanderRocketBlock;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class InstallRocketPacket {
    private final BlockPos launcherPos;
    private final BlockPos terminalPos;

    public InstallRocketPacket(BlockPos launcherPos, BlockPos terminalPos) {
        this.launcherPos = launcherPos;
        this.terminalPos = terminalPos;
    }

    public InstallRocketPacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
        this.terminalPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
        buf.writeBlockPos(terminalPos);
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

            if (launcherState.getValue(LauncherBlock.PART) != LauncherBlock.LauncherPart.CENTER) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.invalid_launcher"), true);
                return;
            }

            if (launcherState.getValue(LauncherBlock.LOADED)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.already_loaded"), true);
                return;
            }

            if (launcher.isBlocked(level, launcherPos)) {
                player.displayClientMessage(Component.translatable("message.ata.launcher.blocked"), true);
                return;
            }

            int rocketSlot = -1;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (stack.is(ModItems.ISKANDER.get())) {
                    rocketSlot = i;
                    break;
                }
            }

            if (rocketSlot == -1) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.no_rocket"), true);
                return;
            }

            player.getInventory().items.get(rocketSlot).shrink(1);

            Direction facing = launcherState.getValue(LauncherBlock.FACING);
            if (IskanderRocketBlock.placeRocket(level, launcherPos, facing)) {

                launcher.setLoaded(level, launcherPos, true);
                player.displayClientMessage(Component.translatable("message.ata.rocket.installed"), true);
            } else {

                player.getInventory().add(new ItemStack(ModItems.ISKANDER.get()));
                player.displayClientMessage(Component.translatable("message.ata.rocket.install_failed"), true);
            }
        });
        context.setPacketHandled(true);
    }
}
