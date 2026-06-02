package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record RadarUpdatePacket(BlockPos radarPos, List<TargetData> targets) implements CustomPacketPayload {

    public static final class TargetData {
        public final int entityId;
        public final byte type;
        public final Vec3 pos;
        public final float heading;
        public final float speed;

        public TargetData(int entityId, byte type, Vec3 pos, float heading, float speed) {
            this.entityId = entityId;
            this.type = type;
            this.pos = pos;
            this.heading = heading;
            this.speed = speed;
        }
    }

    public static final Type<RadarUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "radar_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadarUpdatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.radarPos);
                buf.writeInt(p.targets.size());
                for (TargetData t : p.targets) {
                    buf.writeInt(t.entityId);
                    buf.writeByte(t.type);
                    buf.writeDouble(t.pos.x);
                    buf.writeDouble(t.pos.y);
                    buf.writeDouble(t.pos.z);
                    buf.writeFloat(t.heading);
                    buf.writeFloat(t.speed);
                }
            },
            (buf) -> {
                BlockPos radarPos = buf.readBlockPos();
                int count = buf.readInt();
                List<TargetData> targets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    targets.add(new TargetData(
                            buf.readInt(),
                            buf.readByte(),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readFloat(),
                            buf.readFloat()
                    ));
                }
                return new RadarUpdatePacket(radarPos, targets);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RadarUpdatePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.gui.ShahedRadarScreen.updateTargets(
                payload.radarPos(), payload.targets()));
    }
}
