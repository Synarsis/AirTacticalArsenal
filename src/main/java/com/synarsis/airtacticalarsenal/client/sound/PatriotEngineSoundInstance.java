package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PatriotEngineSoundInstance extends AbstractTickableSoundInstance {

    private final PatriotVehicleEntity vehicle;

    public PatriotEngineSoundInstance(PatriotVehicleEntity vehicle) {
        super(ModSounds.TEL_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.3f;
        this.pitch = 0.7f;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved() || !this.vehicle.isEngineRunning()) {
            this.stop();
            return;
        }
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY() + 1.5;
        this.z = this.vehicle.getZ();
        double load = Math.min(this.vehicle.getEngineLoad(), 1.3);
        this.volume = (float) (0.3 + 0.7 * load);
        double revs = Mth.clamp((this.vehicle.getEngineRpm() - 600.0) / 1500.0, 0.0, 1.2);
        this.pitch = (float) (0.7 + 0.5 * revs);
    }
}
