package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShahedHitPacket(int partOrdinal, float healthPct, boolean knockedOut, boolean penetrated)
        implements CustomPacketPayload {

    public static final Type<ShahedHitPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "shahed_hit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedHitPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ShahedHitPacket::partOrdinal,
                    ByteBufCodecs.FLOAT, ShahedHitPacket::healthPct,
                    ByteBufCodecs.BOOL, ShahedHitPacket::knockedOut,
                    ByteBufCodecs.BOOL, ShahedHitPacket::penetrated,
                    ShahedHitPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShahedHitPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.hud.HitFeedback.onHit(payload));
    }
}
