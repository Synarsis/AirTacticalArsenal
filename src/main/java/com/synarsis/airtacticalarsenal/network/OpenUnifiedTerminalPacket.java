package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenUnifiedTerminalPacket(
        BlockPos terminalPos,
        List<BlockPos> launcherPositions,
        List<Boolean> launcherLoadedStates,
        List<Boolean> launcherLaunchingStates,
        boolean hasLauncher,
        int launcherCount,
        int freeLaunchers,
        int targetY,
        boolean blacklistEnabled,
        boolean whitelistEnabled,
        List<String> blacklistZones,
        List<String> whitelistZones,
        List<BlockPos> launcherTargets,
        List<Double> launcherDistances,
        List<Double> launcherCEPs
) implements CustomPacketPayload {

    public static final Type<OpenUnifiedTerminalPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "open_unified_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUnifiedTerminalPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.terminalPos);
                buf.writeInt(p.launcherPositions.size());
                for (BlockPos pos : p.launcherPositions) {
                    buf.writeBlockPos(pos);
                }
                buf.writeInt(p.launcherLoadedStates.size());
                for (Boolean loaded : p.launcherLoadedStates) {
                    buf.writeBoolean(loaded != null && loaded);
                }
                buf.writeInt(p.launcherLaunchingStates.size());
                for (Boolean launching : p.launcherLaunchingStates) {
                    buf.writeBoolean(launching != null && launching);
                }
                buf.writeBoolean(p.hasLauncher);
                buf.writeInt(p.launcherCount);
                buf.writeInt(p.freeLaunchers);
                buf.writeInt(p.targetY);
                buf.writeBoolean(p.blacklistEnabled);
                buf.writeBoolean(p.whitelistEnabled);

                buf.writeInt(p.blacklistZones.size());
                for (String zone : p.blacklistZones) {
                    buf.writeUtf(zone);
                }

                buf.writeInt(p.whitelistZones.size());
                for (String zone : p.whitelistZones) {
                    buf.writeUtf(zone);
                }

                buf.writeInt(p.launcherTargets.size());
                for (int i = 0; i < p.launcherTargets.size(); i++) {
                    BlockPos target = p.launcherTargets.get(i);
                    if (target != null) {
                        buf.writeBoolean(true);
                        buf.writeBlockPos(target);
                        buf.writeDouble(p.launcherDistances.get(i));
                        buf.writeDouble(p.launcherCEPs.get(i));
                    } else {
                        buf.writeBoolean(false);
                    }
                }
            },
            (buf) -> {
                BlockPos terminalPos = buf.readBlockPos();
                int launcherListSize = buf.readInt();
                List<BlockPos> launcherPositions = new ArrayList<>();
                for (int i = 0; i < launcherListSize; i++) {
                    launcherPositions.add(buf.readBlockPos());
                }
                int loadedStatesSize = buf.readInt();
                List<Boolean> launcherLoadedStates = new ArrayList<>();
                for (int i = 0; i < loadedStatesSize; i++) {
                    launcherLoadedStates.add(buf.readBoolean());
                }
                int launchingStatesSize = buf.readInt();
                List<Boolean> launcherLaunchingStates = new ArrayList<>();
                for (int i = 0; i < launchingStatesSize; i++) {
                    launcherLaunchingStates.add(buf.readBoolean());
                }
                boolean hasLauncher = buf.readBoolean();
                int launcherCount = buf.readInt();
                int freeLaunchers = buf.readInt();
                int targetY = buf.readInt();
                boolean blacklistEnabled = buf.readBoolean();
                boolean whitelistEnabled = buf.readBoolean();

                int blacklistSize = buf.readInt();
                List<String> blacklistZones = new ArrayList<>();
                for (int i = 0; i < blacklistSize; i++) {
                    blacklistZones.add(buf.readUtf());
                }

                int whitelistSize = buf.readInt();
                List<String> whitelistZones = new ArrayList<>();
                for (int i = 0; i < whitelistSize; i++) {
                    whitelistZones.add(buf.readUtf());
                }

                int targetsSize = buf.readInt();
                List<BlockPos> launcherTargets = new ArrayList<>();
                List<Double> launcherDistances = new ArrayList<>();
                List<Double> launcherCEPs = new ArrayList<>();
                for (int i = 0; i < targetsSize; i++) {
                    boolean hasTarget = buf.readBoolean();
                    if (hasTarget) {
                        launcherTargets.add(buf.readBlockPos());
                        launcherDistances.add(buf.readDouble());
                        launcherCEPs.add(buf.readDouble());
                    } else {
                        launcherTargets.add(null);
                        launcherDistances.add(0.0);
                        launcherCEPs.add(0.0);
                    }
                }

                return new OpenUnifiedTerminalPacket(terminalPos, launcherPositions, launcherLoadedStates,
                        launcherLaunchingStates, hasLauncher, launcherCount, freeLaunchers,
                        targetY,
                        blacklistEnabled, whitelistEnabled, blacklistZones, whitelistZones,
                        launcherTargets, launcherDistances, launcherCEPs);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public BlockPos getTerminalPos() { return terminalPos; }
    public List<BlockPos> getLauncherPositions() { return launcherPositions; }
    public BlockPos getLauncherPos() { return launcherPositions.isEmpty() ? BlockPos.ZERO : launcherPositions.get(0); }
    public boolean hasLauncher() { return hasLauncher; }
    public int getLauncherCount() { return launcherCount; }
    public int getFreeLaunchers() { return freeLaunchers; }
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

    public static void handle(OpenUnifiedTerminalPacket payload, IPayloadContext context) {
        // Client-only handler; see the note in ClientScreens.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            ClientPacketHandler.handleOpenUnifiedTerminal(payload);
        });
    }
}
