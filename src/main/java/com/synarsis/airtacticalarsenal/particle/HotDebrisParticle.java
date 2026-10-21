package com.synarsis.airtacticalarsenal.particle;

import com.synarsis.airtacticalarsenal.client.fx.FxSpawn;
import com.synarsis.airtacticalarsenal.client.fx.SmokePresets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HotDebrisParticle extends AtaParticle {

    public static SpriteSet sprites;

    private static final float GRAVITY = 0.05f;

    private final float spin;
    private boolean bounced;
    private boolean resting;

    public HotDebrisParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            int lifetime, SpriteSet set) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = Math.max(10, lifetime);
        this.quadSize = 0.14f + this.random.nextFloat() * 0.1f;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        this.spin = (this.random.nextFloat() - 0.5f) * 0.5f;
        this.pickSprite(set);
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
        if (!this.resting) {
            this.oRoll = this.roll;
            this.roll += this.spin;
            this.yd -= GRAVITY;
            this.xd *= 0.99;
            this.yd *= 0.98;
            this.zd *= 0.99;

            double nx = this.x + this.xd;
            double ny = this.y + this.yd;
            double nz = this.z + this.zd;
            if (this.solidAt(nx, ny, nz)) {
                if (this.yd < 0.0 && this.solidAt(this.x, ny, this.z)) {
                    if (!this.bounced) {
                        this.yd = -this.yd * 0.45;
                        this.xd *= 0.7;
                        this.zd *= 0.7;
                        this.bounced = true;
                    } else {
                        this.yd = 0.0;
                        this.xd *= 0.4;
                        this.zd *= 0.4;
                        this.resting = true;
                    }
                    ny = this.y;
                } else {
                    this.xd = -this.xd * 0.3;
                    this.zd = -this.zd * 0.3;
                    nx = this.x;
                    nz = this.z;
                }
            }
            this.x = nx;
            this.y = ny;
            this.z = nz;
            this.setPos(this.x, this.y, this.z);

            if (this.age % 2 == 0 && !this.resting) {
                FxSpawn.smoke(this.level, SmokePresets.debrisTrail(60 + this.random.nextInt(20)), this.x, this.y, this.z,
                        0.0, 0.0, 0.0);
            }
        }
        if (this.random.nextInt(20) == 0) {
            FxSpawn.wink(this.level, this.x, this.y + 0.05, this.z, 0.18f);
        }
        float life = (float) this.age / this.lifetime;
        this.alpha = life < 0.8f ? 1.0f : 1.0f - (life - 0.8f) / 0.2f;
    }

    @Override
    protected int getLightColor(float partialTick) {
        int base = super.getLightColor(partialTick);
        return LightTexture.pack(Math.max(LightTexture.block(base), 10), LightTexture.sky(base));
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet set;

        public Provider(SpriteSet set) {
            this.set = set;
            sprites = set;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz) {
            return new HotDebrisParticle(level, x, y, z, vx, vy, vz, 100, this.set);
        }
    }
}
