package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.debug.DebugOverrides;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DebugOverridesPacket(boolean noLaunchLimits) implements CustomPacketPayload {

    public static final Type<DebugOverridesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "debug_overrides"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugOverridesPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DebugOverridesPacket::noLaunchLimits,
                    DebugOverridesPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DebugOverridesPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> DebugOverrides.setNoLaunchLimits(payload.noLaunchLimits()));
    }
}
