package com.synarsis.airtacticalarsenal.particle;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AirTacticalArsenal.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEBRIS =
            PARTICLES.register("debris", () -> new SimpleParticleType(false));

    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}
