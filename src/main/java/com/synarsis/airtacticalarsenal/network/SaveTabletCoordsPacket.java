package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.item.ControlTabletItem;
import com.synarsis.airtacticalarsenal.item.OrlanTabletItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SaveTabletCoordsPacket {

    private final Map<Integer, int[]> coords;

    public SaveTabletCoordsPacket(Map<Integer, int[]> coords) {
        this.coords = coords;
    }

    public SaveTabletCoordsPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.coords = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int index = buf.readInt();
            int x = buf.readInt();
            int z = buf.readInt();
            coords.put(index, new int[]{x, z});
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(coords.size());
        for (Map.Entry<Integer, int[]> entry : coords.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeInt(entry.getValue()[0]);
            buf.writeInt(entry.getValue()[1]);
        }
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

            if (tablet != null) {
                if (isOrlan) {
                    OrlanTabletItem.setSavedCoords(tablet, coords);
                } else {
                    ControlTabletItem.setSavedCoords(tablet, coords);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
