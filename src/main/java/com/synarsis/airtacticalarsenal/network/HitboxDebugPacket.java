package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Turns the hit-geometry wireframe on or off, for the one player who ran {@code /ata debug
 * hitbox}.
 */
public record HitboxDebugPacket(boolean enabled) implements CustomPacketPayload {

    public static final Type<HitboxDebugPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "hitbox_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HitboxDebugPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, HitboxDebugPacket::enabled,
                    HitboxDebugPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HitboxDebugPacket payload, IPayloadContext context) {
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.synarsis.airtacticalarsenal.client.debug.HitboxDebugRenderer.setEnabled(payload.enabled()));
    }
}
