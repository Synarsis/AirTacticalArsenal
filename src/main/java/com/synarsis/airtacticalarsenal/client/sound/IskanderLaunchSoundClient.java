package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IskanderLaunchSoundClient extends AbstractTickableSoundInstance {
    private final int entityId;
    private final Vec3 startPos;
    private final BlockPos targetPos;
    private boolean shouldStop = false;
    private boolean exploded = false;
    private boolean stopped = false;
    private long startTime;
    private long creationTime;
    private Vec3 explosionPos = null;
    private long explosionTime = 0L;
    private float pitchAtExplosion = 1.0f;
    private float volumeAtExplosion = 1.0f;
    private static final double MAX_SOUND_DISTANCE = 200.0;

    private Vec3 currentRocketPos;
    private boolean isLateJoin = false;

    public IskanderLaunchSoundClient(int entityId, Vec3 currentPos, BlockPos targetPos) {

        super(ModSounds.ISKANDER_LAUNCH.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.entityId = entityId;
        this.startPos = currentPos;
        this.currentRocketPos = currentPos;
        this.targetPos = targetPos;
        this.looping = false;  
        this.delay = 0;
        this.volume = 1.0f;    
        this.pitch = 1.0f;

        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.creationTime = System.currentTimeMillis();
        this.startTime = this.creationTime;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            double distToStart = mc.player.position().distanceTo(currentPos);
            if (distToStart > 100) {
                this.isLateJoin = true;
                int soundDelayTicks = (int)(distToStart / 17.0);
                this.startTime = this.creationTime - (soundDelayTicks * 50L);
            }
        }

        this.x = currentPos.x;
        this.y = currentPos.y;
        this.z = currentPos.z;
    }

    public void updateRocketPosition(Vec3 serverPos) {

        double dist = this.currentRocketPos.distanceTo(serverPos);
        if (dist > 5.0) {

            this.currentRocketPos = serverPos;
        } else {

            this.currentRocketPos = this.currentRocketPos.lerp(serverPos, 0.5);
        }

        this.x = this.currentRocketPos.x;
        this.y = this.currentRocketPos.y;
        this.z = this.currentRocketPos.z;
    }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(this);
        }
    }

    public int getEntityId() {
        return this.entityId;
    }

    public boolean isExploded() {
        return this.exploded;
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y;
    }

    @Override
    public double getZ() {
        return this.z;
    }

    @Override
    public void tick() {
        if (this.stopped) {
            return;
        }
        if (this.shouldStop && !this.exploded) {
            this.volume = Math.max(0.0f, this.volume - 0.1f);
            if (this.volume <= 0.0f) {
                this.stopSoundCompletely();
            }
            return;
        }
        if (this.exploded) {
            this.handleExplosionSound();
            return;
        }
        this.updatePosition();
    }

    private void stopSoundCompletely() {
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    private void handleExplosionSound() {
        long currentTime = System.currentTimeMillis();
        long timeSinceExplosion = currentTime - this.explosionTime;

        long fadeTimeMs = 1500;

        if (timeSinceExplosion > fadeTimeMs) {
            this.forceStop();
            return;
        }

        float fadeProgress = (float) timeSinceExplosion / (float) fadeTimeMs;
        fadeProgress = Math.min(1.0f, Math.max(0.0f, fadeProgress));

        this.pitch = this.pitchAtExplosion * (1.0f - fadeProgress * 0.3f);
        this.volume = this.volumeAtExplosion * (1.0f - fadeProgress) * 0.5f;

        if (this.volume < 0.01f) {
            this.forceStop();
        }
    }

    private void forceStop() {
        this.volume = 0.0f;
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    private void updatePosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            this.stopSound();
            return;
        }
        LocalPlayer player = mc.player;

        if (mc.level != null) {
            net.minecraft.world.entity.Entity entity = mc.level.getEntity(this.entityId);
            if (entity != null && !entity.isRemoved()) {
                Vec3 realPos = entity.position();
                this.currentRocketPos = this.currentRocketPos.lerp(realPos, 0.7);
                this.x = this.currentRocketPos.x;
                this.y = this.currentRocketPos.y;
                this.z = this.currentRocketPos.z;
            } else if (entity != null && entity.isRemoved()) {
                this.markExploded(this.currentRocketPos);
                return;
            }
        }

    }

    public void markExploded(Vec3 position) {
        if (this.exploded || this.stopped) {
            return;
        }

        this.pitchAtExplosion = this.pitch;
        this.volumeAtExplosion = Math.min(this.volume, 3.0f);
        this.exploded = true;
        this.explosionPos = position;
        this.explosionTime = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            double dist = mc.player.position().distanceTo(position);
            if (dist < 50.0) {
                this.forceStop();
            }
        }
    }

    public void stopSound() {
        if (!this.exploded) {
            this.shouldStop = true;
        }
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }
}
