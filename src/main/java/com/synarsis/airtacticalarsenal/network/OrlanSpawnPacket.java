package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OrlanSpawnPacket(
        int entityId,
        double x, double y, double z,
        double motionX, double motionY, double motionZ,
        BlockPos target,
        int phaseOrdinal,
        int remainingTicks
) implements CustomPacketPayload {

    public static final Type<OrlanSpawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "orlan_spawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrlanSpawnPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OrlanSpawnPacket decode(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double mx = buf.readDouble();
            double my = buf.readDouble();
            double mz = buf.readDouble();
            BlockPos target = buf.readBlockPos();
            int phase = buf.readInt();
            int remaining = buf.readInt();
            return new OrlanSpawnPacket(entityId, x, y, z, mx, my, mz, target, phase, remaining);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OrlanSpawnPacket p) {
            buf.writeInt(p.entityId);
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeDouble(p.motionX);
            buf.writeDouble(p.motionY);
            buf.writeDouble(p.motionZ);
            buf.writeBlockPos(p.target);
            buf.writeInt(p.phaseOrdinal);
            buf.writeInt(p.remainingTicks);
        }
    };

    public OrlanSpawnPacket(int entityId, Vec3 pos, Vec3 motion, BlockPos target, int phaseOrdinal, int remainingTicks) {
        this(entityId, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z, target, phaseOrdinal, remainingTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrlanSpawnPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleOrlanSpawn(
                    payload.entityId(), payload.x(), payload.y(), payload.z(),
                    payload.motionX(), payload.motionY(), payload.motionZ(),
                    payload.phaseOrdinal(), payload.remainingTicks());
        });
    }
}
