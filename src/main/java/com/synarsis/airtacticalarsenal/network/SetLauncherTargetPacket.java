package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetLauncherTargetPacket {
    private final BlockPos launcherPos;
    private final BlockPos targetPos;
    private final boolean hasTarget;

    public SetLauncherTargetPacket(BlockPos launcherPos, BlockPos targetPos) {
        this.launcherPos = launcherPos;
        this.targetPos = targetPos;
        this.hasTarget = targetPos != null;
    }

    public SetLauncherTargetPacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
        this.hasTarget = buf.readBoolean();
        if (hasTarget) {
            this.targetPos = buf.readBlockPos();
        } else {
            this.targetPos = null;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
        buf.writeBoolean(hasTarget);
        if (hasTarget && targetPos != null) {
            buf.writeBlockPos(targetPos);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(launcherPos);

            if (be instanceof LauncherBlockEntity launcher) {
                launcher.setTarget(targetPos);

                if (targetPos != null) {
                    player.displayClientMessage(Component.literal("§aЦель установлена: [" 
                        + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + "]"), true);
                } else {
                    player.displayClientMessage(Component.literal("§eЦель сброшена"), true);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
