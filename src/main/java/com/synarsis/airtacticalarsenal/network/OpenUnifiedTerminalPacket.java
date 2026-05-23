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

public class OpenUnifiedTerminalPacket {
    private final BlockPos terminalPos;
    private final List<BlockPos> launcherPositions;
    private final List<Boolean> launcherLoadedStates;
    private final List<Boolean> launcherLaunchingStates;
    private final boolean hasLauncher;
    private final int launcherCount;
    private final int freeLaunchers;
    private final int launchCostShahed;
    private final int launchCostIskander;
    private final int targetY;
    private final boolean blacklistEnabled;
    private final boolean whitelistEnabled;
    private final List<String> blacklistZones;
    private final List<String> whitelistZones;

    private final List<BlockPos> launcherTargets; 
    private final List<Double> launcherDistances;
    private final List<Double> launcherCEPs;

    public OpenUnifiedTerminalPacket(BlockPos terminalPos, List<BlockPos> launcherPositions, List<Boolean> launcherLoadedStates,
                                     List<Boolean> launcherLaunchingStates,
                                     boolean hasLauncher, int launcherCount, int freeLaunchers, 
                                     int launchCostShahed, int launchCostIskander, int targetY,
                                     boolean blacklistEnabled, boolean whitelistEnabled,
                                     List<String> blacklistZones, List<String> whitelistZones,
                                     List<BlockPos> launcherTargets, List<Double> launcherDistances, List<Double> launcherCEPs) {
        this.terminalPos = terminalPos;
        this.launcherPositions = launcherPositions != null ? launcherPositions : new ArrayList<>();
        this.launcherLoadedStates = launcherLoadedStates != null ? launcherLoadedStates : new ArrayList<>();
        this.launcherLaunchingStates = launcherLaunchingStates != null ? launcherLaunchingStates : new ArrayList<>();
        this.hasLauncher = hasLauncher;
        this.launcherCount = launcherCount;
        this.freeLaunchers = freeLaunchers;
        this.launchCostShahed = launchCostShahed;
        this.launchCostIskander = launchCostIskander;
        this.targetY = targetY;
        this.blacklistEnabled = blacklistEnabled;
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistZones = blacklistZones;
        this.whitelistZones = whitelistZones;
        this.launcherTargets = launcherTargets != null ? launcherTargets : new ArrayList<>();
        this.launcherDistances = launcherDistances != null ? launcherDistances : new ArrayList<>();
        this.launcherCEPs = launcherCEPs != null ? launcherCEPs : new ArrayList<>();
    }

    public OpenUnifiedTerminalPacket(FriendlyByteBuf buf) {
        this.terminalPos = buf.readBlockPos();
        int launcherListSize = buf.readInt();
        this.launcherPositions = new ArrayList<>();
        for (int i = 0; i < launcherListSize; i++) {
            this.launcherPositions.add(buf.readBlockPos());
        }
        int loadedStatesSize = buf.readInt();
        this.launcherLoadedStates = new ArrayList<>();
        for (int i = 0; i < loadedStatesSize; i++) {
            this.launcherLoadedStates.add(buf.readBoolean());
        }
        int launchingStatesSize = buf.readInt();
        this.launcherLaunchingStates = new ArrayList<>();
        for (int i = 0; i < launchingStatesSize; i++) {
            this.launcherLaunchingStates.add(buf.readBoolean());
        }
        this.hasLauncher = buf.readBoolean();
        this.launcherCount = buf.readInt();
        this.freeLaunchers = buf.readInt();
        this.launchCostShahed = buf.readInt();
        this.launchCostIskander = buf.readInt();
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

        int targetsSize = buf.readInt();
        this.launcherTargets = new ArrayList<>();
        this.launcherDistances = new ArrayList<>();
        this.launcherCEPs = new ArrayList<>();
        for (int i = 0; i < targetsSize; i++) {
            boolean hasTarget = buf.readBoolean();
            if (hasTarget) {
                this.launcherTargets.add(buf.readBlockPos());
                this.launcherDistances.add(buf.readDouble());
                this.launcherCEPs.add(buf.readDouble());
            } else {
                this.launcherTargets.add(null);
                this.launcherDistances.add(0.0);
                this.launcherCEPs.add(0.0);
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(terminalPos);
        buf.writeInt(launcherPositions.size());
        for (BlockPos pos : launcherPositions) {
            buf.writeBlockPos(pos);
        }
        buf.writeInt(launcherLoadedStates.size());
        for (Boolean loaded : launcherLoadedStates) {
            buf.writeBoolean(loaded != null && loaded);
        }
        buf.writeInt(launcherLaunchingStates.size());
        for (Boolean launching : launcherLaunchingStates) {
            buf.writeBoolean(launching != null && launching);
        }
        buf.writeBoolean(hasLauncher);
        buf.writeInt(launcherCount);
        buf.writeInt(freeLaunchers);
        buf.writeInt(launchCostShahed);
        buf.writeInt(launchCostIskander);
        buf.writeInt(targetY);
        buf.writeBoolean(blacklistEnabled);
        buf.writeBoolean(whitelistEnabled);

        buf.writeInt(blacklistZones.size());
        for (String zone : blacklistZones) {
            buf.writeUtf(zone);
        }

        buf.writeInt(whitelistZones.size());
        for (String zone : whitelistZones) {
            buf.writeUtf(zone);
        }

        buf.writeInt(launcherTargets.size());
        for (int i = 0; i < launcherTargets.size(); i++) {
            BlockPos target = launcherTargets.get(i);
            if (target != null) {
                buf.writeBoolean(true);
                buf.writeBlockPos(target);
                buf.writeDouble(launcherDistances.get(i));
                buf.writeDouble(launcherCEPs.get(i));
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(this));
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenUnifiedTerminalPacket packet) {
        com.synarsis.airtacticalarsenal.network.ClientPacketHandler.handleOpenUnifiedTerminal(packet);
    }

    public BlockPos getTerminalPos() { return terminalPos; }
    public List<BlockPos> getLauncherPositions() { return launcherPositions; }
    public BlockPos getLauncherPos() { return launcherPositions.isEmpty() ? BlockPos.ZERO : launcherPositions.get(0); }
    public boolean hasLauncher() { return hasLauncher; }
    public int getLauncherCount() { return launcherCount; }
    public int getFreeLaunchers() { return freeLaunchers; }
    public int getLaunchCostShahed() { return launchCostShahed; }
    public int getLaunchCostIskander() { return launchCostIskander; }
    public int getTargetY() { return targetY; }
    public boolean isBlacklistEnabled() { return blacklistEnabled; }
    public boolean isWhitelistEnabled() { return whitelistEnabled; }
    public List<String> getBlacklistZones() { return blacklistZones; }
    public List<String> getWhitelistZones() { return whitelistZones; }
    public List<Boolean> getLauncherLoadedStates() { return launcherLoadedStates; }
    public List<Boolean> getLauncherLaunchingStates() { return launcherLaunchingStates; }

    public List<BlockPos> getLauncherTargets() { return launcherTargets; }
    public List<Double> getLauncherDistances() { return launcherDistances; }
    public List<Double> getLauncherCEPs() { return launcherCEPs; }
}
