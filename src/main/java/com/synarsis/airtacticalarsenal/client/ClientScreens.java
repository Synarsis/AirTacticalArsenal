package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.gui.OrlanTabletScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedRadarScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedTabletScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

public final class ClientScreens {

    private ClientScreens() {}

    public static void openShahedTerminal(BlockPos terminalPos, int targetY,
                                          boolean blacklistEnabled, boolean whitelistEnabled,
                                          List<String> blacklistZones, List<String> whitelistZones) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ShahedTerminalScreen(terminalPos, targetY,
                blacklistEnabled, whitelistEnabled, blacklistZones, whitelistZones));
    }

    public static void openShahedTablet(List<BlockPos> positions, List<Boolean> drones,
                                        List<BlockPos> targets, List<Float> yawList,
                                        Map<Integer, int[]> coords, List<List<BlockPos>> routes,
                                        List<int[]> flyingShaheds) {
        Minecraft.getInstance().setScreen(
                new ShahedTabletScreen(positions, drones, targets, yawList, coords, routes, flyingShaheds));
    }

    public static void openOrlanTablet(List<BlockPos> positions, List<Boolean> drones,
                                       List<Float> yawList, Map<Integer, int[]> coords,
                                       List<int[]> activeOrlans, List<List<BlockPos>> routes) {
        Minecraft.getInstance().setScreen(
                new OrlanTabletScreen(positions, drones, yawList, coords, activeOrlans, routes));
    }

    public static void openRadar(BlockPos radarPos) {
        ShahedRadarScreen.open(radarPos);
    }
}
