package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShahedSpawnPacket {
    private final int entityId;
    private final Vec3 startPos;
    private final BlockPos targetPos;
    private final String diveMode;

    public ShahedSpawnPacket(int entityId, Vec3 startPos, BlockPos targetPos, String diveMode) {
        this.entityId = entityId;
        this.startPos = startPos;
        this.targetPos = targetPos;
        this.diveMode = diveMode;
    }

    public ShahedSpawnPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.targetPos = buf.readBlockPos();
        this.diveMode = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeBlockPos(this.targetPos);
        buf.writeUtf(this.diveMode);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> 
            handleClient(this.entityId, this.startPos, this.targetPos, this.diveMode)));
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(int entityId, Vec3 startPos, BlockPos targetPos, String diveMode) {
        com.synarsis.airtacticalarsenal.network.ClientPacketHandler.handleShahedSpawn(entityId, startPos, targetPos, diveMode);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public Vec3 getStartPos() {
        return this.startPos;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public String getDiveMode() {
        return this.diveMode;
    }
}
