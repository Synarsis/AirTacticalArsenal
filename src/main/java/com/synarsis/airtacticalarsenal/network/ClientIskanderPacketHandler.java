package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.client.sound.IskanderLaunchSoundClient;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class ClientIskanderPacketHandler {

    private static final Map<Integer, IskanderLaunchSoundClient> activeSounds = new HashMap<>();

    private static final java.util.Set<Integer> launchSoundPlayed = new java.util.HashSet<>();
    private static final Random random = new Random();

    public static void handleIskanderSpawn(int entityId, Vec3 currentPos, BlockPos targetPos, int flightPhase,
            int flightTicks, BlockPos launcherPos, Vec3 startPos, Vec3 launchEndPos, Vec3 finalTargetPos) {
        cleanupStoppedSounds();

        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof IskanderEntity iskander) {
                iskander.setServerPosition(currentPos);
                iskander.syncTrajectoryData(startPos, launchEndPos, finalTargetPos);
            }
        }

        IskanderLaunchSoundClient existingSound = activeSounds.get(entityId);
        if (existingSound != null) {
            if (existingSound.isStopped()) {
                activeSounds.remove(entityId);
            } else {

                existingSound.updateRocketPosition(currentPos);
                return;
            }
        }

        if (flightPhase >= 1) {

            if (!launchSoundPlayed.contains(entityId)) {
                launchSoundPlayed.add(entityId);

                ClientRocketPacketHandler.stopPreparingSound(launcherPos);

                IskanderLaunchSoundClient sound = new IskanderLaunchSoundClient(entityId, currentPos, targetPos);
                activeSounds.put(entityId, sound);
                mc.execute(sound::start);
            }
        }
    }

    public static void handleIskanderRemove(int entityId) {
        launchSoundPlayed.remove(entityId);
        IskanderLaunchSoundClient sound = activeSounds.remove(entityId);
        if (sound != null && !sound.isStopped()) {
            sound.stopSound();
        }
    }

    private static void cleanupStoppedSounds() {
        Iterator<Map.Entry<Integer, IskanderLaunchSoundClient>> iterator = activeSounds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, IskanderLaunchSoundClient> entry = iterator.next();
            if (entry.getValue().isStopped()) {
                iterator.remove();
            }
        }
    }

    public static void handleExplosion(Vec3 explosionPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 listenerPos = (mc.cameraEntity != null) ? mc.cameraEntity.position() : mc.player.position();
        double distance = listenerPos.distanceTo(explosionPos);
        float explosionVolume = calculateExplosionVolume(distance);

        if (explosionVolume < 0.1f) {
            return;
        }

        if (!ShahedConfig.areCustomExplosionSoundsEnabled()) {
            return;
        }

        int delay = (int) (distance / 17.0);
        int randomSound = random.nextInt(5);

        new Thread(() -> {
            try {
                Thread.sleep((long) delay * 50L);
                mc.execute(() -> {
                    float randomPitch = 0.85f + random.nextFloat() * 0.15f;
                    var soundEvent = switch (randomSound) {
                        case 0 -> ModSounds.EXPLOSION_1.get();
                        case 1 -> ModSounds.EXPLOSION_2.get();
                        case 2 -> ModSounds.EXPLOSION_3.get();
                        case 3 -> ModSounds.EXPLOSION_4.get();
                        default -> ModSounds.EXPLOSION_5.get();
                    };
                    mc.level.playLocalSound(explosionPos.x, explosionPos.y, explosionPos.z,
                            soundEvent, SoundSource.HOSTILE, explosionVolume * 1.3f, randomPitch, false);
                });
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    private static float calculateExplosionVolume(double distance) {
        if (distance >= 800.0)
            return 0.0f;
        if (distance <= 400.0)
            return 50.0f;
        if (distance <= 500.0)
            return 30.0f;
        if (distance <= 600.0)
            return 15.0f;
        if (distance <= 700.0)
            return 6.0f;
        return 2.0f;
    }

    public static void clearAll() {
        launchSoundPlayed.clear();
        for (IskanderLaunchSoundClient sound : activeSounds.values()) {
            if (!sound.isStopped()) {
                sound.stopSound();
            }
        }
        activeSounds.clear();
    }
}
