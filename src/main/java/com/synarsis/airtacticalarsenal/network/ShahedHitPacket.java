package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells a shooter that their round landed, and on what.
 *
 * @param partOrdinal the section struck, or {@code -1} for damage with no direction to it
 * @param healthPct what is left of the airframe, 0..1, for the marker's colour
 * @param knockedOut whether this is the blow that took the section out
 * @param penetrated false when the shot was too weak to do anything, so a ricochet can be shown
 * rather than nothing at all
 */
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
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.hud.HitFeedback.onHit(payload));
    }
}
