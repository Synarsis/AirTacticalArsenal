package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LauncherStateUpdatePacket(
        BlockPos launcherPos,
        LauncherState state,
        int countdownTicks,
        int totalCountdownTicks,
        int flightTicks
) implements CustomPacketPayload {

    public enum LauncherState {
        EMPTY,
        LOADED,
        COUNTDOWN,
        IN_FLIGHT
    }

    public static final Type<LauncherStateUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "launcher_state_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LauncherStateUpdatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.launcherPos);
                buf.writeInt(p.state.ordinal());
                buf.writeInt(p.countdownTicks);
                buf.writeInt(p.totalCountdownTicks);
                buf.writeInt(p.flightTicks);
            },
            (buf) -> new LauncherStateUpdatePacket(
                    buf.readBlockPos(),
                    LauncherState.values()[buf.readInt()],
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public BlockPos getLauncherPos() { return launcherPos; }
    public LauncherState getState() { return state; }
    public int getCountdownTicks() { return countdownTicks; }
    public int getTotalCountdownTicks() { return totalCountdownTicks; }
    public int getFlightTicks() { return flightTicks; }

    public static void handle(LauncherStateUpdatePacket payload, IPayloadContext context) {
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            ClientPacketHandler.handleLauncherStateUpdate(
                    payload.launcherPos(), payload.state(),
                    payload.countdownTicks(), payload.totalCountdownTicks(), payload.flightTicks());
        });
    }
}
