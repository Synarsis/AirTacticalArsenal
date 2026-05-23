package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class IskanderSpawnPacket {
    private final int entityId;
    private final Vec3 position;
    private final BlockPos targetPos;
    private final int flightPhase;
    private final int flightTicks;
    private final BlockPos launcherPos; 
    private final Vec3 startPos;
    private final Vec3 launchEndPos;
    private final Vec3 finalTargetPos;

    public IskanderSpawnPacket(int entityId, Vec3 position, BlockPos targetPos, int flightPhase, int flightTicks,
            BlockPos launcherPos, Vec3 startPos, Vec3 launchEndPos, Vec3 finalTargetPos) {
        this.entityId = entityId;
        this.position = position;
        this.targetPos = targetPos;
        this.flightPhase = flightPhase;
        this.flightTicks = flightTicks;
        this.launcherPos = launcherPos;
        this.startPos = startPos;
        this.launchEndPos = launchEndPos;
        this.finalTargetPos = finalTargetPos;
    }

    public IskanderSpawnPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.targetPos = buf.readBlockPos();
        this.flightPhase = buf.readInt();
        this.flightTicks = buf.readInt();
        this.launcherPos = buf.readBoolean() ? buf.readBlockPos() : null;
        this.startPos = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
        this.launchEndPos = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
        this.finalTargetPos = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeBlockPos(targetPos);
        buf.writeInt(flightPhase);
        buf.writeInt(flightTicks);
        buf.writeBoolean(launcherPos != null);
        if (launcherPos != null) {
            buf.writeBlockPos(launcherPos);
        }
        buf.writeBoolean(startPos != null);
        if (startPos != null) {
            buf.writeDouble(startPos.x);
            buf.writeDouble(startPos.y);
            buf.writeDouble(startPos.z);
        }
        buf.writeBoolean(launchEndPos != null);
        if (launchEndPos != null) {
            buf.writeDouble(launchEndPos.x);
            buf.writeDouble(launchEndPos.y);
            buf.writeDouble(launchEndPos.z);
        }
        buf.writeBoolean(finalTargetPos != null);
        if (finalTargetPos != null) {
            buf.writeDouble(finalTargetPos.x);
            buf.writeDouble(finalTargetPos.y);
            buf.writeDouble(finalTargetPos.z);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientIskanderPacketHandler.handleIskanderSpawn(entityId, position, targetPos, flightPhase, flightTicks,
                    launcherPos, startPos, launchEndPos, finalTargetPos);
        });
        ctx.get().setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public Vec3 getPosition() {
        return position;
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public int getFlightPhase() {
        return flightPhase;
    }

    public int getFlightTicks() {
        return flightTicks;
    }

    public BlockPos getLauncherPos() {
        return launcherPos;
    }

    public Vec3 getFinalTargetPos() {
        return finalTargetPos;
    }
}
