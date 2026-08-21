package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.gui.OrlanTabletScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedRadarScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedTabletScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Opens the mod's screens. Client-only, and never referenced from a class the server loads.
 *
 * <p>The screen-opening packet handlers used to construct screens inline. That put a reference to
 * {@code Screen} into the handler's own bytecode, and registering the handler on a dedicated
 * server made FML's dist cleaner refuse to load the class -- so
 * {@code RegisterPayloadHandlersEvent} threw and <em>the whole mod failed to load</em>. It did not
 * degrade; the server simply would not start.
 *
 * <p>The indirection is what keeps that from happening: the handlers now mention only this class
 * by name, and a name is resolved lazily, on first call -- which happens only on a client.
 */
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
