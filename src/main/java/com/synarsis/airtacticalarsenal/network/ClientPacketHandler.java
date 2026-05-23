package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.client.ClientWorldMapData;
import com.synarsis.airtacticalarsenal.client.gui.UnifiedTerminalScreen;
import com.synarsis.airtacticalarsenal.client.sound.OrlanEngineSoundClient;
import com.synarsis.airtacticalarsenal.client.sound.ShahedEngineSoundClient;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
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
public class ClientPacketHandler {
    private static final Map<Integer, ShahedEngineSoundClient> activeSounds = new HashMap<>();
    private static final Map<Integer, OrlanEngineSoundClient> activeOrlanSounds = new HashMap<>();
    private static final Random random = new Random();
    private static final int ORLAN_LANDED_PHASE = 6;

    private static final Map<BlockPos, LauncherStateUpdatePacket.LauncherState> launcherStates = new HashMap<>();
    private static final Map<BlockPos, int[]> launcherTimers = new HashMap<>(); 

    private static final Map<Integer, int[]> orlanDataCache = new HashMap<>();

    public static void handleShahedSpawn(int entityId, Vec3 currentPos, BlockPos targetPos, String diveMode) {
        cleanupStoppedSounds();

        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof ShahedEntity shahed) {
                shahed.lerpTo(currentPos.x, currentPos.y, currentPos.z, shahed.getYRot(), shahed.getXRot(), 3, false);
            }
        }

        ShahedEngineSoundClient existingSound = activeSounds.get(entityId);
        if (existingSound != null) {
            if (existingSound.isStopped()) {
                activeSounds.remove(entityId);
            } else {
                existingSound.updateDronePosition(currentPos);
                return;
            }
        }

        ShahedEngineSoundClient sound = new ShahedEngineSoundClient(entityId, currentPos, targetPos, diveMode);
        activeSounds.put(entityId, sound);
        mc.execute(sound::start);
    }

    public static void handleShahedRemove(int entityId) {
        ShahedEngineSoundClient sound = activeSounds.get(entityId);
        if (sound != null) {
            if (sound.isExploded()) {

                return;
            }

            sound.stopSound();
            sound.markEntityRemovedLocally();
        }
    }

    public static void handleExplosion(Vec3 explosionPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        ShahedEngineSoundClient closestSound = null;
        double closestDistance = Double.MAX_VALUE;
        int closestEntityId = -1;

        for (Map.Entry<Integer, ShahedEngineSoundClient> entry : activeSounds.entrySet()) {
            ShahedEngineSoundClient sound = entry.getValue();
            if (sound.isStopped())
                continue;

            Vec3 soundPos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            double dist = soundPos.distanceTo(explosionPos);
            if (dist < closestDistance && dist < 150.0) {
                closestDistance = dist;
                closestSound = sound;
                closestEntityId = entry.getKey();
            }
        }

        if (closestSound != null) {
            closestSound.markExploded(explosionPos);
            final int entityId = closestEntityId;
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                Minecraft.getInstance().execute(() -> activeSounds.remove(entityId));
            }).start();
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
                    float randomPitch = 0.9f + random.nextFloat() * 0.2f;
                    SoundEvent soundEvent = switch (randomSound) {
                        case 0 -> ModSounds.EXPLOSION_1.get();
                        case 1 -> ModSounds.EXPLOSION_2.get();
                        case 2 -> ModSounds.EXPLOSION_3.get();
                        case 3 -> ModSounds.EXPLOSION_4.get();
                        default -> ModSounds.EXPLOSION_5.get();
                    };
                    mc.level.playLocalSound(explosionPos.x, explosionPos.y, explosionPos.z, soundEvent,
                            SoundSource.HOSTILE, explosionVolume, randomPitch, false);
                });
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    private static float calculateExplosionVolume(double distance) {
        if (distance >= 700.0)
            return 0.0f;
        if (distance <= 350.0)
            return 40.0f;
        if (distance <= 450.0)
            return 24.0f;
        if (distance <= 550.0)
            return 12.0f;
        if (distance <= 650.0)
            return 4.0f;
        return 1.0f;
    }

    private static void cleanupStoppedSounds() {
        Iterator<Map.Entry<Integer, ShahedEngineSoundClient>> iterator = activeSounds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ShahedEngineSoundClient> entry = iterator.next();
            if (entry.getValue().isStopped()) {
                iterator.remove();
            }
        }
    }

    public static void clearAll() {
        for (ShahedEngineSoundClient sound : activeSounds.values()) {
            if (!sound.isStopped()) {
                sound.stopSound();
            }
        }
        activeSounds.clear();
        for (OrlanEngineSoundClient sound : activeOrlanSounds.values()) {
            if (!sound.isStopped()) {
                sound.forceStop();
            }
        }
        activeOrlanSounds.clear();
    }

    public static void handleWorldMapPacket(WorldMapPacket packet) {
        ClientWorldMapData.getInstance().update(
                packet.getCenterX(),
                packet.getCenterZ(),
                packet.getRadius(),
                packet.getCoastlinePoints(),
                packet.isScanComplete(),
                packet.getScanProgress(),
                packet.getTotalChunks());
    }

    public static void handleOpenUnifiedTerminal(OpenUnifiedTerminalPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            UnifiedTerminalScreen screen = new UnifiedTerminalScreen(
                    packet.getTerminalPos(),
                    packet.getLauncherPositions(),
                    packet.getLauncherLoadedStates(),
                    packet.getLauncherLaunchingStates(),
                    packet.hasLauncher(),
                    packet.getLauncherCount(),
                    packet.getFreeLaunchers(),
                    packet.getLaunchCostShahed(),
                    packet.getLaunchCostIskander(),
                    packet.getTargetY(),
                    packet.isBlacklistEnabled(),
                    packet.isWhitelistEnabled(),
                    packet.getBlacklistZones(),
                    packet.getWhitelistZones());

            screen.loadTargetsFromServer(
                    packet.getLauncherPositions(),
                    packet.getLauncherTargets(),
                    packet.getLauncherDistances(),
                    packet.getLauncherCEPs());

            mc.setScreen(screen);
        });
    }

    public static void handleLauncherStateUpdate(BlockPos launcherPos, LauncherStateUpdatePacket.LauncherState state,
            int countdownTicks, int totalCountdownTicks, int flightTicks) {
        launcherStates.put(launcherPos, state);
        launcherTimers.put(launcherPos, new int[] { countdownTicks, totalCountdownTicks, flightTicks });

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof UnifiedTerminalScreen terminalScreen) {
            terminalScreen.updateLauncherState(launcherPos, state, countdownTicks, totalCountdownTicks, flightTicks);
        }
    }

    public static LauncherStateUpdatePacket.LauncherState getLauncherState(BlockPos pos) {
        return launcherStates.getOrDefault(pos, LauncherStateUpdatePacket.LauncherState.EMPTY);
    }

    public static int[] getLauncherTimers(BlockPos pos) {
        return launcherTimers.getOrDefault(pos, new int[] { 0, 0, 0 });
    }

    public static void clearLauncherState(BlockPos pos) {
        launcherStates.remove(pos);
        launcherTimers.remove(pos);
    }

    public static void handleOrlanSpawn(int entityId, double x, double y, double z, double motionX, double motionY,
            double motionZ, int phaseOrdinal, int remainingTicks) {

        orlanDataCache.put(entityId, new int[] { phaseOrdinal, remainingTicks });

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof com.synarsis.airtacticalarsenal.entity.OrlanEntity orlan) {
                orlan.setServerPosition(new Vec3(x, y, z));
                orlan.setDeltaMovement(motionX, motionY, motionZ);
            }
        }

        boolean isLanded = (phaseOrdinal == ORLAN_LANDED_PHASE);
        OrlanEngineSoundClient existingSound = activeOrlanSounds.get(entityId);

        if (!isLanded) {
            if (existingSound == null || existingSound.isStopped()) {
                OrlanEngineSoundClient sound = new OrlanEngineSoundClient(entityId, x, y, z);
                activeOrlanSounds.put(entityId, sound);
                mc.execute(sound::start);
            } else {
                existingSound.updateFromPacket(x, y, z);
            }
        } else {

            if (existingSound != null && !existingSound.isStopped()) {
                existingSound.stopSound();
                activeOrlanSounds.remove(entityId);
            }
        }
    }

    public static int[] getCachedOrlanData(int entityId) {
        return orlanDataCache.get(entityId);
    }

    public static void handleOrlanRemove(int entityId) {
        orlanDataCache.remove(entityId);
        OrlanEngineSoundClient sound = activeOrlanSounds.remove(entityId);
        if (sound != null && !sound.isStopped()) {
            sound.forceStop();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity != null) {
                entity.discard();
            }
        }
    }
}
