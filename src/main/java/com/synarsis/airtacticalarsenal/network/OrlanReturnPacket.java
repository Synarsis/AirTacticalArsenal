package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OrlanReturnPacket {
    private final int entityId;

    public OrlanReturnPacket(int entityId) {
        this.entityId = entityId;
    }

    public OrlanReturnPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(entityId);

            if (!(entity instanceof OrlanEntity orlan)) {
                player.displayClientMessage(Component.literal("§cДрон не найден!"), true);
                return;
            }

            if (!orlan.isOwnedBy(player)) {
                player.displayClientMessage(Component.literal("§cЭто не ваш дрон!"), true);
                return;
            }

            OrlanEntity.FlightPhase phase = orlan.getFlightPhase();
            if (phase == OrlanEntity.FlightPhase.RETURNING
                    || phase == OrlanEntity.FlightPhase.LANDING
                    || phase == OrlanEntity.FlightPhase.LANDED) {
                player.displayClientMessage(Component.literal("§eДрон уже возвращается"), true);
                return;
            }

            orlan.forceReturn();
            player.displayClientMessage(Component.literal("§aОрлан-10 возвращается на базу"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
