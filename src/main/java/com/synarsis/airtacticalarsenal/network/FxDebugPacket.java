package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FxDebugPacket(byte kind, double x, double y, double z, float power) implements CustomPacketPayload {

    public static final Type<FxDebugPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "fx_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FxDebugPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, FxDebugPacket::kind,
            ByteBufCodecs.DOUBLE, FxDebugPacket::x,
            ByteBufCodecs.DOUBLE, FxDebugPacket::y,
            ByteBufCodecs.DOUBLE, FxDebugPacket::z,
            ByteBufCodecs.FLOAT, FxDebugPacket::power,
            FxDebugPacket::new);

    public static FxDebugPacket of(FxDebugKind kind, Vec3 at, double power) {
        return new FxDebugPacket((byte) kind.ordinal(), at.x, at.y, at.z, (float) power);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FxDebugPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.fx.FxDebugScenes.play(
                FxDebugKind.byOrdinal(payload.kind()), new Vec3(payload.x(), payload.y(), payload.z()), payload.power()));
    }
}
