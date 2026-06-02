package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.ControlTabletItem;
import com.synarsis.airtacticalarsenal.item.OrlanTabletItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RemoveLauncherFromTabletPacket(BlockPos launcherPos) implements CustomPacketPayload {

    public static final Type<RemoveLauncherFromTabletPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "remove_launcher_from_tablet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveLauncherFromTabletPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RemoveLauncherFromTabletPacket::launcherPos,
            RemoveLauncherFromTabletPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveLauncherFromTabletPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

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
                    removed = OrlanTabletItem.removeLinkedLauncher(tablet, payload.launcherPos());
                } else {
                    removed = ControlTabletItem.removeLinkedLauncher(tablet, payload.launcherPos());
                }
                if (removed) {
                    player.displayClientMessage(Component.literal("§eПУ отвязана от планшета"), true);
                }
            }
        });
    }
}
