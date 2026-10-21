package com.synarsis.airtacticalarsenal.particle;

import com.mojang.serialization.MapCodec;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AirTacticalArsenal.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEBRIS =
            PARTICLES.register("debris", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, ParticleType<AtaSmokeOptions>> SMOKE =
            PARTICLES.register("smoke", () -> new ParticleType<AtaSmokeOptions>(true) {
                @Override
                public MapCodec<AtaSmokeOptions> codec() {
                    return AtaSmokeOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, AtaSmokeOptions> streamCodec() {
                    return AtaSmokeOptions.STREAM_CODEC;
                }
            });
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLASH =
            PARTICLES.register("flash", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE =
            PARTICLES.register("fire", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK =
            PARTICLES.register("spark", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCK_RING =
            PARTICLES.register("shock_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOT_DEBRIS =
            PARTICLES.register("hot_debris", () -> new SimpleParticleType(true));

    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}
