package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChangeShahedAltitudePacket {

    private final int entityId;
    private final int altitude;

    public ChangeShahedAltitudePacket(int entityId, int altitude) {
        this.entityId = entityId;
        this.altitude = altitude;
    }

    public ChangeShahedAltitudePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.altitude = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(altitude);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof ShahedEntity shahed)) return;

            if (shahed.getOwner() != player) return;

            if (shahed.getFlightPhase() != ShahedEntity.FlightPhase.CRUISING) return;

            shahed.setTargetAltitude(altitude);
        });
        ctx.get().setPacketHandled(true);
    }
}
