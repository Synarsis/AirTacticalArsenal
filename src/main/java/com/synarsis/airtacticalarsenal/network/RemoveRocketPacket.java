package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.block.IskanderRocketBlock;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RemoveRocketPacket {
    private final BlockPos launcherPos;

    public RemoveRocketPacket(BlockPos launcherPos) {
        this.launcherPos = launcherPos;
    }

    public RemoveRocketPacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
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

            level.removeBlock(rocketPos, false);

            launcher.setLoaded(level, launcherPos, false);

            ItemStack rocketItem = new ItemStack(ModItems.ISKANDER.get());
            if (!player.getInventory().add(rocketItem)) {
                player.drop(rocketItem, false);
            }

            player.displayClientMessage(Component.translatable("message.ata.rocket.removed"), true);
        });
        context.setPacketHandled(true);
    }
}
