package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShahedRemovePacket {
    private final int entityId;

    public ShahedRemovePacket(int entityId) {
        this.entityId = entityId;
    }

    public ShahedRemovePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(this.entityId)));
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(int entityId) {
        com.synarsis.airtacticalarsenal.network.ClientPacketHandler.handleShahedRemove(entityId);
    }

    public int getEntityId() {
        return this.entityId;
    }
}
