package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.particle.AtaSmokeParticle;
import com.synarsis.airtacticalarsenal.particle.FireParticle;
import com.synarsis.airtacticalarsenal.particle.FlashParticle;
import com.synarsis.airtacticalarsenal.particle.HotDebrisParticle;
import com.synarsis.airtacticalarsenal.particle.ShockRingParticle;
import com.synarsis.airtacticalarsenal.particle.SparkParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FxSpawn {

    private FxSpawn() {
    }

    public static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static void add(Particle particle) {
        if (FxBudget.live() > FxBudget.CAP + FxBudget.ESSENTIAL_ROOM) {
            particle.remove();
            return;
        }
        Minecraft.getInstance().particleEngine.add(particle);
    }

    public static void smoke(ClientLevel level, SmokeParams params, double x, double y, double z, double vx, double vy,
            double vz) {
        if (level != null && AtaSmokeParticle.sprites != null) {
            add(new AtaSmokeParticle(level, x, y, z, vx, vy, vz, params, AtaSmokeParticle.sprites));
        }
    }

    public static void flash(ClientLevel level, double x, double y, double z, float size, int ticks) {
        if (level != null && FlashParticle.sprites != null) {
            add(new FlashParticle(level, x, y, z, size, ticks, FlashParticle.sprites));
        }
    }

    public static FlashParticle glow(ClientLevel level, double x, double y, double z, float size, int ticks, float alpha,
            float red, float green, float blue, boolean linear) {
        if (level == null || FlashParticle.sprites == null) {
            return null;
        }
        FlashParticle halo = new FlashParticle(level, x, y, z, size, ticks, FlashParticle.sprites)
                .glow(alpha, red, green, blue, linear);
        add(halo);
        return halo.isAlive() ? halo : null;
    }

    public static void fire(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            float size0, float size1, int lifetime, float drag) {
        if (level != null && FireParticle.sprites != null) {
            add(new FireParticle(level, x, y, z, vx, vy, vz, size0, size1, lifetime, drag, FireParticle.sprites));
        }
    }

    public static void spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            float size, int lifetime, float pull) {
        if (level != null && SparkParticle.sprites != null) {
            add(new SparkParticle(level, x, y, z, vx, vy, vz, size, lifetime, pull, SparkParticle.sprites));
        }
    }

    public static void wink(ClientLevel level, double x, double y, double z, float size) {
        if (level != null && SparkParticle.sprites != null) {
            add(SparkParticle.wink(level, x, y, z, size, SparkParticle.sprites));
        }
    }

    public static void ring(ClientLevel level, double x, double y, double z, float size0, float size1, int lifetime) {
        if (level != null && ShockRingParticle.sprites != null) {
            add(new ShockRingParticle(level, x, y, z, size0, size1, lifetime, ShockRingParticle.sprites));
        }
    }

    public static void debris(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            int lifetime) {
        if (level != null && HotDebrisParticle.sprites != null) {
            add(new HotDebrisParticle(level, x, y, z, vx, vy, vz, lifetime, HotDebrisParticle.sprites));
        }
    }

    public static void blockDust(ClientLevel level, BlockState state, double x, double y, double z, double vx,
            double vy, double vz) {
        if (level != null && !state.isAir()) {
            level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, vx, vy, vz);
        }
    }
}
