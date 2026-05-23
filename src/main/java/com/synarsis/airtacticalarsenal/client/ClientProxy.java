package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.gui.ShahedTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientProxy {
    public static void openTerminalScreen(BlockPos terminalPos, int launchCost, int targetY,
                                          boolean blacklistEnabled, boolean whitelistEnabled,
                                          List<String> blacklistZones, List<String> whitelistZones) {
        Minecraft.getInstance().setScreen(new ShahedTerminalScreen(terminalPos, launchCost, targetY,
            blacklistEnabled, whitelistEnabled, blacklistZones, whitelistZones));
    }
}
