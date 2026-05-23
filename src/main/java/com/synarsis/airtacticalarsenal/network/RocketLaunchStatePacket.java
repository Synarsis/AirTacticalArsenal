package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RocketLaunchStatePacket {
    private final BlockPos pos;
    private final int launchTicks;
    private final int totalTicks;
    private final boolean launching;

    public RocketLaunchStatePacket(BlockPos pos, int launchTicks, int totalTicks, boolean launching) {
        this.pos = pos;
        this.launchTicks = launchTicks;
        this.totalTicks = totalTicks;
        this.launching = launching;
    }

    public RocketLaunchStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.launchTicks = buf.readInt();
        this.totalTicks = buf.readInt();
        this.launching = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(launchTicks);
        buf.writeInt(totalTicks);
        buf.writeBoolean(launching);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                ClientRocketPacketHandler.handleLaunchState(pos, launchTicks, totalTicks, launching));
        });
        context.setPacketHandled(true);
    }
}
