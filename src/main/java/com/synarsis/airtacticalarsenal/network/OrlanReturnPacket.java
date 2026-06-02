package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OrlanReturnPacket(int orlanEntityId) implements CustomPacketPayload {

    public static final Type<OrlanReturnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "orlan_return"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrlanReturnPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OrlanReturnPacket::orlanEntityId,
            OrlanReturnPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrlanReturnPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(payload.orlanEntityId());

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
    }
}
