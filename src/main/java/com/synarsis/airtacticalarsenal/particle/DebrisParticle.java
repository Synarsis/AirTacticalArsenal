package com.synarsis.airtacticalarsenal.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DebrisParticle extends TextureSheetParticle {
    private final float rotationSpeed;

    protected DebrisParticle(ClientLevel level, double x, double y, double z, 
                             double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx * 1.5;
        this.yd = vy * 1.5;
        this.zd = vz * 1.5;
        this.lifetime = 150;
        this.hasPhysics = true;
        this.gravity = 0.05f;
        this.quadSize = 0.25f + this.random.nextFloat() * 0.15f;
        this.rotationSpeed = (this.random.nextFloat() - 0.5f) * 0.4f;

        float brightness = 0.9f + this.random.nextFloat() * 0.1f;
        this.rCol = brightness;
        this.gCol = brightness;
        this.bCol = brightness;
        this.alpha = 1.0f;

        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        this.oRoll = this.roll;
        this.roll += this.rotationSpeed;

        this.yd -= this.gravity;

        this.move(this.xd, this.yd, this.zd);

        if (this.onGround) {
            this.xd *= 0.7;
            this.zd *= 0.7;
            this.yd = 0;
        }

        if (this.age > this.lifetime * 0.5) {
            this.alpha = Math.max(0, this.alpha - 0.025f);
            if (this.alpha <= 0) {
                this.remove();
            }
        }

        this.xd *= 0.98;
        this.yd *= 0.98;
        this.zd *= 0.98;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new DebrisParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
