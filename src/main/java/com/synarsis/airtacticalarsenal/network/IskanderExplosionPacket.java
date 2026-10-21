package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record IskanderExplosionPacket(Vec3 explosionPos) implements CustomPacketPayload {
    public static final Type<IskanderExplosionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "iskander_explosion"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IskanderExplosionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, p -> p.explosionPos.x,
            ByteBufCodecs.DOUBLE, p -> p.explosionPos.y,
            ByteBufCodecs.DOUBLE, p -> p.explosionPos.z,
            (x, y, z) -> new IskanderExplosionPacket(new Vec3(x, y, z))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Vec3 getExplosionPos() {
        return explosionPos;
    }

    public static void handle(IskanderExplosionPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            ClientIskanderPacketHandler.handleExplosion(payload.explosionPos());
        });
    }
}
