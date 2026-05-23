package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.synarsis.airtacticalarsenal.client.sound.IskanderPreparingSoundClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientRocketPacketHandler {

    private static final Map<BlockPos, IskanderPreparingSoundClient> activeSounds = new HashMap<>();

    public static void handleLaunchState(BlockPos pos, int launchTicks, int totalTicks, boolean launching) {
        cleanupStoppedSounds();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof IskanderRocketBlockEntity rocketEntity) {
            rocketEntity.setLaunching(launching);
            rocketEntity.setLaunchTicks(launchTicks);

            IskanderPreparingSoundClient existingSound = activeSounds.get(pos);
            if (existingSound != null) {
                if (existingSound.isStopped()) {
                    activeSounds.remove(pos);
                } else if (!launching) {

                    existingSound.stopSound();
                    return;
                } else {

                    return;
                }
            }

            if (launching && launchTicks == 0) {
                IskanderPreparingSoundClient sound = new IskanderPreparingSoundClient(pos);
                activeSounds.put(pos, sound);
                mc.execute(sound::start);
            }
        }
    }

    private static void cleanupStoppedSounds() {
        Iterator<Map.Entry<BlockPos, IskanderPreparingSoundClient>> iterator = activeSounds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, IskanderPreparingSoundClient> entry = iterator.next();
            if (entry.getValue().isStopped()) {
                iterator.remove();
            }
        }
    }

    public static void stopPreparingSound(BlockPos pos) {
        if (pos == null) return;
        IskanderPreparingSoundClient sound = activeSounds.remove(pos);
        if (sound != null && !sound.isStopped()) {
            sound.stopSound();
        }
    }

    public static void clearAll() {
        for (IskanderPreparingSoundClient sound : activeSounds.values()) {
            if (!sound.isStopped()) {
                sound.stopSound();
            }
        }
        activeSounds.clear();
    }
}
