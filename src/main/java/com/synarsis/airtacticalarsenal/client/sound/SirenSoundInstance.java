package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SirenSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, SirenSoundInstance> activeSounds = new HashMap<>();
    private static final double MAX_DISTANCE = 300.0;

    private final BlockPos sirenPos;
    private final Vec3 sirenVec;
    private boolean forceStopped = false;

    public SirenSoundInstance(BlockPos pos) {
        super(ModSounds.SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.sirenPos = pos;
        this.sirenVec = Vec3.atCenterOf(pos);

        this.relative = false;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;

        this.looping = false;
        this.delay = 0;
        this.pitch = 1.0f;
        this.attenuation = Attenuation.LINEAR;

        this.volume = calculateVolume();
    }

    private float calculateVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0f;

        double distance = mc.player.position().distanceTo(this.sirenVec);

        if (distance >= MAX_DISTANCE) return 0.0f;
        if (distance <= 10) return 2.0f;

        double factor = 1.0 - ((distance - 10.0) / (MAX_DISTANCE - 10.0));
        return (float) (factor * 2.0);
    }

    public static void playSiren(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
        if (distance > MAX_DISTANCE) return;

        SirenSoundInstance old = activeSounds.remove(pos);
        if (old != null) {
            old.forceStopped = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(old);
            }
        }

        SirenSoundInstance sound = new SirenSoundInstance(pos);
        activeSounds.put(pos, sound);
        mc.getSoundManager().play(sound);
    }

    public static void stopSiren(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();

        SirenSoundInstance sound = activeSounds.remove(pos);
        if (sound != null) {
            sound.forceStopped = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(sound);
            }
        }
    }

    @Override
    public void tick() {
        if (this.forceStopped) return;

        this.volume = calculateVolume();

        if (this.volume <= 0.001f) {
            this.forceStopped = true;
            activeSounds.remove(this.sirenPos);
        }
    }

    @Override
    public boolean isStopped() {
        return this.forceStopped;
    }
}
