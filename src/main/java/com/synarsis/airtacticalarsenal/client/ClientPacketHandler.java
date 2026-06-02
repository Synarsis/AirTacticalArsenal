package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.gui.OrlanTabletScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedTabletScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Helpers invoked from server-to-client tablet open packets to actually push a screen.
 * The richer {@code com.synarsis.airtacticalarsenal.network.ClientPacketHandler} (sound /
 * launcher state caches) ports separately when its referenced packets land.
 */
public class ClientPacketHandler {

    public static void openShahedTabletScreen(List<BlockPos> positions, List<Boolean> drones,
                                               List<BlockPos> targets, List<Float> yawList, Map<Integer, int[]> coords,
                                               List<List<BlockPos>> routes, List<int[]> flyingShaheds) {
        Minecraft.getInstance().setScreen(new ShahedTabletScreen(positions, drones, targets, yawList, coords, routes, flyingShaheds));
    }

    public static void openOrlanTabletScreen(List<BlockPos> positions, List<Boolean> drones,
                                              List<Float> yawList, Map<Integer, int[]> coords,
                                              List<int[]> activeOrlans, List<List<BlockPos>> routes) {
        Minecraft.getInstance().setScreen(new OrlanTabletScreen(positions, drones, yawList, coords, activeOrlans, routes));
    }
}
