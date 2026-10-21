package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TelEngineSoundInstance extends AbstractTickableSoundInstance {

    private final IskanderTelEntity tel;

    public TelEngineSoundInstance(IskanderTelEntity tel) {
        super(ModSounds.TEL_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.tel = tel;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.3f;
        this.pitch = 0.7f;
        this.x = tel.getX();
        this.y = tel.getY();
        this.z = tel.getZ();
    }

    @Override
    public void tick() {
        if (this.tel.isRemoved() || !this.tel.isEngineRunning()) {
            this.stop();
            return;
        }
        this.x = this.tel.getX();
        this.y = this.tel.getY() + 1.5;
        this.z = this.tel.getZ();

        double load = Math.min(Math.abs(this.tel.engine.power), 1.3);
        this.volume = (float) (0.3 + 0.7 * load);
        this.pitch = (float) (0.7 + 0.5 * Mth.clamp(this.tel.getSpeedKmh() / 60.0, 0.0, 1.5));
    }
}
