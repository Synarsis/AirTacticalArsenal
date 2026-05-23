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
public class IskanderPreparingSoundClient extends AbstractTickableSoundInstance {
    private final BlockPos blockPos;
    private boolean shouldStop = false;
    private boolean stopped = false;
    private long startTime;
    private long creationTime;

    private static final float INITIAL_VOLUME = 1.0f;

    private Vec3 soundPos;

    public IskanderPreparingSoundClient(BlockPos pos) {
        super(ModSounds.ISKANDER_PREPARING.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.blockPos = pos;
        this.soundPos = Vec3.atCenterOf(pos);
        this.looping = true;  
        this.delay = 0;
        this.volume = INITIAL_VOLUME;
        this.pitch = 1.0f;    

        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.creationTime = System.currentTimeMillis();
        this.startTime = this.creationTime;

        this.x = this.soundPos.x;
        this.y = this.soundPos.y;
        this.z = this.soundPos.z;
    }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(this);
        }
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
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

    }

    public void stopSound() {
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }
}
