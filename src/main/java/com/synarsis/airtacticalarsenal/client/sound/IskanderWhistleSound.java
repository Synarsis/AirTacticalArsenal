package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IskanderWhistleSound extends AbstractTickableSoundInstance {

    private static final float QUIET = 3.0f;
    private static final float LOUD = 12.5f;
    private static final int RISE_TICKS = 80;

    private final IskanderEntity missile;
    private int age;

    public IskanderWhistleSound(IskanderEntity missile) {
        super(ModSounds.ISKANDER_TERMINAL.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.missile = missile;
        this.looping = false;
        this.delay = 0;
        this.volume = QUIET;
        this.pitch = 1.0f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = missile.getX();
        this.y = missile.getY();
        this.z = missile.getZ();
    }

    @Override
    public void tick() {
        if (this.missile.isRemoved()) {
            this.stop();
            return;
        }
        this.x = this.missile.getX();
        this.y = this.missile.getY();
        this.z = this.missile.getZ();
        this.age++;
        float rise = Math.min(1.0f, (float) this.age / RISE_TICKS);
        this.volume = QUIET + (LOUD - QUIET) * rise * rise;
        this.pitch = 0.9f + 0.3f * rise;
    }
}
