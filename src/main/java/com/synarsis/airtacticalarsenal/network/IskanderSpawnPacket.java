package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record IskanderSpawnPacket(
        int entityId,
        Vec3 position,
        BlockPos targetPos,
        int flightPhase,
        int flightTicks,
        BlockPos launcherPos,
        Vec3 startPos,
        Vec3 launchEndPos,
        Vec3 finalTargetPos
) implements CustomPacketPayload {

    public static final Type<IskanderSpawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "iskander_spawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IskanderSpawnPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.entityId);
                buf.writeDouble(p.position.x);
                buf.writeDouble(p.position.y);
                buf.writeDouble(p.position.z);
                buf.writeBlockPos(p.targetPos);
                buf.writeInt(p.flightPhase);
                buf.writeInt(p.flightTicks);
                buf.writeBoolean(p.launcherPos != null);
                if (p.launcherPos != null) {
                    buf.writeBlockPos(p.launcherPos);
                }
                buf.writeBoolean(p.startPos != null);
                if (p.startPos != null) {
                    buf.writeDouble(p.startPos.x);
                    buf.writeDouble(p.startPos.y);
                    buf.writeDouble(p.startPos.z);
                }
                buf.writeBoolean(p.launchEndPos != null);
                if (p.launchEndPos != null) {
                    buf.writeDouble(p.launchEndPos.x);
                    buf.writeDouble(p.launchEndPos.y);
                    buf.writeDouble(p.launchEndPos.z);
                }
                buf.writeBoolean(p.finalTargetPos != null);
                if (p.finalTargetPos != null) {
                    buf.writeDouble(p.finalTargetPos.x);
                    buf.writeDouble(p.finalTargetPos.y);
                    buf.writeDouble(p.finalTargetPos.z);
                }
            },
            (buf) -> {
                int entityId = buf.readInt();
                Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                BlockPos targetPos = buf.readBlockPos();
                int flightPhase = buf.readInt();
                int flightTicks = buf.readInt();
                BlockPos launcherPos = buf.readBoolean() ? buf.readBlockPos() : null;
                Vec3 startPos = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
                Vec3 launchEndPos = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
                Vec3 finalTargetPos = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
                return new IskanderSpawnPacket(entityId, position, targetPos, flightPhase, flightTicks,
                        launcherPos, startPos, launchEndPos, finalTargetPos);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() { return entityId; }
    public Vec3 getPosition() { return position; }
    public BlockPos getTargetPos() { return targetPos; }
    public int getFlightPhase() { return flightPhase; }
    public int getFlightTicks() { return flightTicks; }
    public BlockPos getLauncherPos() { return launcherPos; }
    public Vec3 getFinalTargetPos() { return finalTargetPos; }

    public static void handle(IskanderSpawnPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientIskanderPacketHandler.handleIskanderSpawn(
                    payload.entityId(), payload.position(), payload.targetPos(),
                    payload.flightPhase(), payload.flightTicks(), payload.launcherPos(),
                    payload.startPos(), payload.launchEndPos(), payload.finalTargetPos());
        });
    }
}
