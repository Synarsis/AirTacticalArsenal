package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.synarsis.airtacticalarsenal.client.gui.SurfaceHeightCache.updateSegmentHeight(
                        payload.segmentIndex(), payload.maxY(), payload.heightProfile()));
    }
}
