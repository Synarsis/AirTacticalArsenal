package com.synarsis.airtacticalarsenal.particle;

import com.mojang.serialization.MapCodec;
import com.synarsis.airtacticalarsenal.client.fx.SmokeParams;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.StreamCodec;

public record AtaSmokeOptions(SmokeParams params) implements ParticleOptions {

    public static final MapCodec<AtaSmokeOptions> CODEC = SmokeParams.CODEC.xmap(AtaSmokeOptions::new,
            AtaSmokeOptions::params);

    public static final StreamCodec<ByteBuf, AtaSmokeOptions> STREAM_CODEC = SmokeParams.STREAM_CODEC
            .map(AtaSmokeOptions::new, AtaSmokeOptions::params);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.SMOKE.get();
    }
}
