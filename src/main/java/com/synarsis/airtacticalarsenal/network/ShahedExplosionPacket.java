package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShahedExplosionPacket(Vec3 position) implements CustomPacketPayload {
    public static final Type<ShahedExplosionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "shahed_explosion"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedExplosionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, p -> p.position.x,
            ByteBufCodecs.DOUBLE, p -> p.position.y,
            ByteBufCodecs.DOUBLE, p -> p.position.z,
            (x, y, z) -> new ShahedExplosionPacket(new Vec3(x, y, z))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Vec3 getPosition() { return position; }

    public static void handle(ShahedExplosionPacket payload, IPayloadContext context) {
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            ClientPacketHandler.handleExplosion(payload.position());
        });
    }
}
