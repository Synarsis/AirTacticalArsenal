package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class IskanderRemovePacket {
    private final int entityId;

    public IskanderRemovePacket(int entityId) {
        this.entityId = entityId;
    }

    public IskanderRemovePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientIskanderPacketHandler.handleIskanderRemove(entityId);
        });
        ctx.get().setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }
}
