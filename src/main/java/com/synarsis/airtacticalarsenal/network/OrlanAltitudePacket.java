package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OrlanAltitudePacket {
    private final int entityId;
    private final int direction; 

    public OrlanAltitudePacket(int entityId, int direction) {
        this.entityId = entityId;
        this.direction = direction;
    }

    public OrlanAltitudePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.direction = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(direction);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(entityId);

            if (!(entity instanceof OrlanEntity orlan)) return;
            if (!orlan.isOwnedBy(player)) return;

            double current = orlan.getTargetAltitude();
            double step = 5.0 * direction;
            double newAlt = Math.max(100.0, Math.min(350.0, current + step));
            orlan.setTargetAltitude(newAlt);
        });
        ctx.get().setPacketHandled(true);
    }
}
