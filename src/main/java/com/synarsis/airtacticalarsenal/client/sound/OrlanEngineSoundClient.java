package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OrlanEngineSoundClient extends AbstractTickableSoundInstance {

    private final int entityId;
    private boolean stopped = false;
    private int ticksNotFound = 0;
    private static final int MAX_NOT_FOUND = 100;

    public OrlanEngineSoundClient(int entityId, double x, double y, double z) {
        super(ModSounds.ORLAN_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.entityId = entityId;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.5f;
        this.pitch = 1.0f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getEntityId() { return entityId; }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(this);
        }
    }

    public void updateFromPacket(double px, double py, double pz) {
        this.x = px;
        this.y = py;
        this.z = pz;
        this.ticksNotFound = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            double dist = mc.player.position().distanceTo(new Vec3(px, py, pz));
            updateVolumeByDistance(dist);
        }
    }

    @Override
    public void tick() {
        if (stopped) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            forceStop();
            return;
        }

        Entity entity = mc.level.getEntity(entityId);
        if (entity != null && !entity.isRemoved()) {
            ticksNotFound = 0;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
            double dist = mc.player.position().distanceTo(entity.position());
            updateVolumeByDistance(dist);
        } else {
            ticksNotFound++;
            if (ticksNotFound > MAX_NOT_FOUND) {
                forceStop();
            }
        }
    }

    private void updateVolumeByDistance(double dist) {
        if (dist < 30) this.volume = 3.0f;
        else if (dist < 80) this.volume = 2.0f;
        else if (dist < 150) this.volume = 1.2f;
        else if (dist < 300) this.volume = 0.5f;
        else this.volume = 0.15f;
    }

    public void forceStop() {
        this.stopped = true;
        this.volume = 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    public void stopSound() { forceStop(); }

    @Override
    public boolean isStopped() { return stopped; }
}
