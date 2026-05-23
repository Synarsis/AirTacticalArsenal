package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenTerminalScreenPacket {
    private final BlockPos terminalPos;
    private final int launchCost;
    private final int targetY;
    private final boolean blacklistEnabled;
    private final boolean whitelistEnabled;
    private final List<String> blacklistZones;
    private final List<String> whitelistZones;

    public OpenTerminalScreenPacket(BlockPos terminalPos, int launchCost, int targetY, 
                                     boolean blacklistEnabled, boolean whitelistEnabled,
                                     List<String> blacklistZones, List<String> whitelistZones) {
        this.terminalPos = terminalPos;
        this.launchCost = launchCost;
        this.targetY = targetY;
        this.blacklistEnabled = blacklistEnabled;
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistZones = blacklistZones;
        this.whitelistZones = whitelistZones;
    }

    public OpenTerminalScreenPacket(FriendlyByteBuf buf) {
        this.terminalPos = buf.readBlockPos();
        this.launchCost = buf.readInt();
        this.targetY = buf.readInt();
        this.blacklistEnabled = buf.readBoolean();
        this.whitelistEnabled = buf.readBoolean();

        int blacklistSize = buf.readInt();
        this.blacklistZones = new ArrayList<>();
        for (int i = 0; i < blacklistSize; i++) {
            this.blacklistZones.add(buf.readUtf());
        }

        int whitelistSize = buf.readInt();
        this.whitelistZones = new ArrayList<>();
        for (int i = 0; i < whitelistSize; i++) {
            this.whitelistZones.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.terminalPos);
        buf.writeInt(this.launchCost);
        buf.writeInt(this.targetY);
        buf.writeBoolean(this.blacklistEnabled);
        buf.writeBoolean(this.whitelistEnabled);

        buf.writeInt(this.blacklistZones.size());
        for (String zone : this.blacklistZones) {
            buf.writeUtf(zone);
        }

        buf.writeInt(this.whitelistZones.size());
        for (String zone : this.whitelistZones) {
            buf.writeUtf(zone);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> 
            handleClient(this.terminalPos, this.launchCost, this.targetY,
                this.blacklistEnabled, this.whitelistEnabled, this.blacklistZones, this.whitelistZones)));
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(BlockPos terminalPos, int launchCost, int targetY,
                                      boolean blacklistEnabled, boolean whitelistEnabled,
                                      List<String> blacklistZones, List<String> whitelistZones) {
        com.synarsis.airtacticalarsenal.client.ClientProxy.openTerminalScreen(
            terminalPos, launchCost, targetY, blacklistEnabled, whitelistEnabled, blacklistZones, whitelistZones);
    }
}
