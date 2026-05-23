package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RadarUpdatePacket {
    private final BlockPos radarPos;
    private final List<TargetData> targets;

    public static class TargetData {
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

    public RadarUpdatePacket(BlockPos radarPos, List<TargetData> targets) {
        this.radarPos = radarPos;
        this.targets = targets;
    }

    public RadarUpdatePacket(FriendlyByteBuf buf) {
        this.radarPos = buf.readBlockPos();
        int count = buf.readInt();
        this.targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            targets.add(new TargetData(
                    buf.readInt(),
                    buf.readByte(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(),
                    buf.readFloat()
            ));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.radarPos);
        buf.writeInt(this.targets.size());
        for (TargetData t : targets) {
            buf.writeInt(t.entityId);
            buf.writeByte(t.type);
            buf.writeDouble(t.pos.x);
            buf.writeDouble(t.pos.y);
            buf.writeDouble(t.pos.z);
            buf.writeFloat(t.heading);
            buf.writeFloat(t.speed);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(radarPos, targets));
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(BlockPos radarPos, List<TargetData> targets) {
        com.synarsis.airtacticalarsenal.client.gui.ShahedRadarScreen.updateTargets(radarPos, targets);
    }
}
