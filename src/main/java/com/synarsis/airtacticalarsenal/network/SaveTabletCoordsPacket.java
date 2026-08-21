package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.ControlTabletItem;
import com.synarsis.airtacticalarsenal.item.OrlanTabletItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record SaveTabletCoordsPacket(Map<Integer, int[]> savedCoords) implements CustomPacketPayload {

    public static final Type<SaveTabletCoordsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "save_tablet_coords"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveTabletCoordsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.savedCoords.size());
                for (Map.Entry<Integer, int[]> entry : p.savedCoords.entrySet()) {
                    buf.writeInt(entry.getKey());
                    int[] arr = entry.getValue();
                    buf.writeInt(arr.length);
                    for (int v : arr) buf.writeInt(v);
                }
            },
            (buf) -> {
                int n = PacketLimits.readCount(buf, PacketLimits.MAX_SAVED_COORD_SLOTS, "saved coord slot");
                Map<Integer, int[]> map = new HashMap<>();
                for (int i = 0; i < n; i++) {
                    int key = buf.readInt();
                    int len = PacketLimits.readCount(buf, PacketLimits.MAX_SAVED_COORD_VALUES, "saved coord value");
                    int[] arr = new int[len];
                    for (int j = 0; j < len; j++) arr[j] = buf.readInt();
                    map.put(key, arr);
                }
                return new SaveTabletCoordsPacket(map);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveTabletCoordsPacket payload, IPayloadContext context) {
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

            if (tablet != null) {
                if (isOrlan) {
                    OrlanTabletItem.setSavedCoords(tablet, payload.savedCoords());
                } else {
                    ControlTabletItem.setSavedCoords(tablet, payload.savedCoords());
                }
            }
        });
    }
}
