package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LauncherStateUpdatePacket {

    public enum LauncherState {
        EMPTY,      
        LOADED,     
        COUNTDOWN,  
        IN_FLIGHT   
    }

    private final BlockPos launcherPos;
    private final LauncherState state;
    private final int countdownTicks;      
    private final int totalCountdownTicks; 
    private final int flightTicks;         

    public LauncherStateUpdatePacket(BlockPos launcherPos, LauncherState state, 
                                      int countdownTicks, int totalCountdownTicks, int flightTicks) {
        this.launcherPos = launcherPos;
        this.state = state;
        this.countdownTicks = countdownTicks;
        this.totalCountdownTicks = totalCountdownTicks;
        this.flightTicks = flightTicks;
    }

    public LauncherStateUpdatePacket(FriendlyByteBuf buf) {
        this.launcherPos = buf.readBlockPos();
        this.state = LauncherState.values()[buf.readInt()];
        this.countdownTicks = buf.readInt();
        this.totalCountdownTicks = buf.readInt();
        this.flightTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(launcherPos);
        buf.writeInt(state.ordinal());
        buf.writeInt(countdownTicks);
        buf.writeInt(totalCountdownTicks);
        buf.writeInt(flightTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> 
                handleClient(launcherPos, state, countdownTicks, totalCountdownTicks, flightTicks));
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(BlockPos launcherPos, LauncherState state, 
                                      int countdownTicks, int totalCountdownTicks, int flightTicks) {
        com.synarsis.airtacticalarsenal.network.ClientPacketHandler.handleLauncherStateUpdate(
            launcherPos, state, countdownTicks, totalCountdownTicks, flightTicks);
    }

    public BlockPos getLauncherPos() { return launcherPos; }
    public LauncherState getState() { return state; }
    public int getCountdownTicks() { return countdownTicks; }
    public int getTotalCountdownTicks() { return totalCountdownTicks; }
    public int getFlightTicks() { return flightTicks; }
}
