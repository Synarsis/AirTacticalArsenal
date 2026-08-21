package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RocketLaunchStatePacket(BlockPos pos, int launchTicks, int totalTicks, boolean launching) implements CustomPacketPayload {
    public static final Type<RocketLaunchStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "rocket_launch_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RocketLaunchStatePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RocketLaunchStatePacket::pos,
            ByteBufCodecs.INT, RocketLaunchStatePacket::launchTicks,
            ByteBufCodecs.INT, RocketLaunchStatePacket::totalTicks,
            ByteBufCodecs.BOOL, RocketLaunchStatePacket::launching,
            RocketLaunchStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RocketLaunchStatePacket payload, IPayloadContext context) {
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            ClientRocketPacketHandler.handleLaunchState(
                    payload.pos(), payload.launchTicks(), payload.totalTicks(), payload.launching());
        });
    }
}
