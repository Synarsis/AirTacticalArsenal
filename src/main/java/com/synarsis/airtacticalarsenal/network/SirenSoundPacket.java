package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SirenSoundPacket(BlockPos pos, boolean stop) implements CustomPacketPayload {
    public static final Type<SirenSoundPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "siren_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SirenSoundPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SirenSoundPacket::pos,
            ByteBufCodecs.BOOL, SirenSoundPacket::stop,
            SirenSoundPacket::new
    );

    public SirenSoundPacket(BlockPos pos) {
        this(pos, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SirenSoundPacket payload, IPayloadContext context) {
        // Client-only handler. Payload handlers are registered on both sides, and a dedicated
        // server that loads a class referencing client types aborts mod loading entirely -- the
        // server does not start at all. Guard before touching anything client-side.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            if (payload.stop()) {
                com.synarsis.airtacticalarsenal.client.sound.SirenSoundInstance.stopSiren(payload.pos());
            } else {
                com.synarsis.airtacticalarsenal.client.sound.SirenSoundInstance.playSiren(payload.pos());
            }
        });
    }
}
