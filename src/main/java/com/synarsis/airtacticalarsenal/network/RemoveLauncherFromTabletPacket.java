package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.item.ControlTabletItem;
import com.synarsis.airtacticalarsenal.item.OrlanTabletItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RemoveLauncherFromTabletPacket {

    private final BlockPos launcherPos;

    public RemoveLauncherFromTabletPacket(BlockPos launcherPos) {
        this.launcherPos = launcherPos;
    }

    public RemoveLauncherFromTabletPacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);

            ItemStack tablet = null;
            boolean isOrlan = false;
            if (mainHand.getItem() instanceof OrlanTabletItem) {
                tablet = mainHand;
                isOrlan = true;
            } else if (offHand.getItem() instanceof OrlanTabletItem) {
                tablet = offHand;
                isOrlan = true;
            } else if (mainHand.getItem() instanceof ControlTabletItem) {
                tablet = mainHand;
            } else if (offHand.getItem() instanceof ControlTabletItem) {
                tablet = offHand;
            }

            if (tablet == null) {
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() instanceof OrlanTabletItem) {
                        tablet = stack;
                        isOrlan = true;
                        break;
                    } else if (stack.getItem() instanceof ControlTabletItem) {
                        tablet = stack;
                        break;
                    }
                }
            }

            if (tablet != null) {
                boolean removed;
                if (isOrlan) {
                    removed = OrlanTabletItem.removeLinkedLauncher(tablet, launcherPos);
                } else {
                    removed = ControlTabletItem.removeLinkedLauncher(tablet, launcherPos);
                }
                if (removed) {
                    player.displayClientMessage(Component.literal("§eПУ отвязана от планшета"), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
