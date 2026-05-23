package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SegmentMaxHeightPacket {
    private final int x1, z1, x2, z2;
    private final int segmentIndex;
    private final int maxY;
    private final int[] heightProfile;

    public SegmentMaxHeightPacket(int x1, int z1, int x2, int z2, int segmentIndex, int maxY, int[] heightProfile) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.segmentIndex = segmentIndex;
        this.maxY = maxY;
        this.heightProfile = heightProfile;
    }

    public SegmentMaxHeightPacket(FriendlyByteBuf buf) {
        this.x1 = buf.readInt();
        this.z1 = buf.readInt();
        this.x2 = buf.readInt();
        this.z2 = buf.readInt();
        this.segmentIndex = buf.readVarInt();
        this.maxY = buf.readInt();
        int len = buf.readVarInt();
        this.heightProfile = new int[len];
        for (int i = 0; i < len; i++) {
            this.heightProfile[i] = buf.readShort();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x1);
        buf.writeInt(z1);
        buf.writeInt(x2);
        buf.writeInt(z2);
        buf.writeVarInt(segmentIndex);
        buf.writeInt(maxY);
        buf.writeVarInt(heightProfile.length);
        for (int h : heightProfile) {
            buf.writeShort(h);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(segmentIndex, maxY, heightProfile));
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(int segmentIndex, int maxY, int[] heightProfile) {
        com.synarsis.airtacticalarsenal.client.gui.SurfaceHeightCache.updateSegmentHeight(segmentIndex, maxY, heightProfile);
    }
}
