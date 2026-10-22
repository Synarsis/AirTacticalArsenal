package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class M983EngineSoundInstance extends AbstractTickableSoundInstance {

    private final M983TractorEntity tractor;

    public M983EngineSoundInstance(M983TractorEntity tractor) {
        super(ModSounds.TEL_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.tractor = tractor;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.3f;
        this.pitch = 0.7f;
        this.x = tractor.getX();
        this.y = tractor.getY();
        this.z = tractor.getZ();
    }

    @Override
    public void tick() {
        if (this.tractor.isRemoved() || this.tractor.getHealth() <= 0.0f) {
            this.stop();
            return;
        }
        this.x = this.tractor.getX();
        this.y = this.tractor.getY() + 1.5;
        this.z = this.tractor.getZ();
        double load = Math.min(this.tractor.getEngineLoad(), 1.3);
        this.volume = (float) (0.3 + 0.7 * load);
        double revs = Mth.clamp((this.tractor.getEngineRpm() - 600.0) / 1500.0, 0.0, 1.2);
        this.pitch = (float) (0.7 + 0.5 * revs);
    }
}
