package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client segment max-height response. Mirrors the Forge {@code SegmentMaxHeightPacket};
 * carries the per-segment max terrain height and a sampled height profile, updating the client
 * {@code SurfaceHeightCache}.
 *
 * PORT-NOTE: heightProfile entries are transmitted as shorts to match the Forge wire format.
 */
public record SegmentMaxHeightPacket(int x1, int z1, int x2, int z2,
                                     int segmentIndex, int maxY, int[] heightProfile) implements CustomPacketPayload {

    public static final Type<SegmentMaxHeightPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "segment_max_height"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SegmentMaxHeightPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.x1);
                buf.writeInt(p.z1);
                buf.writeInt(p.x2);
                buf.writeInt(p.z2);
                buf.writeVarInt(p.segmentIndex);
                buf.writeInt(p.maxY);
                buf.writeVarInt(p.heightProfile.length);
                for (int h : p.heightProfile) {
                    buf.writeShort(h);
                }
            },
            (buf) -> {
                int x1 = buf.readInt();
                int z1 = buf.readInt();
                int x2 = buf.readInt();
                int z2 = buf.readInt();
                int segmentIndex = buf.readVarInt();
                int maxY = buf.readInt();
                int len = buf.readVarInt();
                int[] heightProfile = new int[len];
                for (int i = 0; i < len; i++) {
                    heightProfile[i] = buf.readShort();
                }
                return new SegmentMaxHeightPacket(x1, z1, x2, z2, segmentIndex, maxY, heightProfile);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SegmentMaxHeightPacket payload, IPayloadContext context) {
        // Client-only handler. Payload handlers are registered on both sides, and a dedicated
        // server that loads a class referencing client types aborts mod loading entirely -- the
        // server does not start at all. Guard before touching anything client-side.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.synarsis.airtacticalarsenal.client.gui.SurfaceHeightCache.updateSegmentHeight(
                        payload.segmentIndex(), payload.maxY(), payload.heightProfile()));
    }
}
