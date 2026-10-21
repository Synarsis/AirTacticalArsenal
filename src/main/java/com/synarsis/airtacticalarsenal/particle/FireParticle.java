package com.synarsis.airtacticalarsenal.particle;

import com.synarsis.airtacticalarsenal.client.fx.FxCurves;
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
public class FireParticle extends AtaParticle {

    public static SpriteSet sprites;

    private final SpriteSet set;
    private final float size0;
    private final float size1;
    private final float drag;

    public FireParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            float size0, float size1, int lifetime, float drag, SpriteSet set) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.size0 = size0;
        this.size1 = size1;
        this.drag = drag;
        this.set = set;
        this.lifetime = Math.max(2, lifetime);
        this.quadSize = size0 * 0.5f;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        this.setSprite(set.get(0, 3));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            if (this.random.nextBoolean()) {
                FxSpawn.smoke(this.level, SmokePresets.fireSmoke(this.size1 * 1.8f, 80 + this.random.nextInt(20)),
                        this.x, this.y, this.z, 0.0, 0.02, 0.0);
            }
            return;
        }
        this.xd *= this.drag;
        this.yd = this.yd * this.drag + 0.004;
        this.zd *= this.drag;
        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;
        this.setPos(this.x, this.y, this.z);

        this.setSprite(this.set.get(FxCurves.fireFrame(this.age, this.lifetime, 4), 3));
        float life = (float) this.age / this.lifetime;
        float[] c = FxCurves.fireColour(life);
        this.rCol = 1.0f + (c[0] - 1.0f) * 0.6f;
        this.gCol = 1.0f + (c[1] - 1.0f) * 0.6f;
        this.bCol = 1.0f + (c[2] - 1.0f) * 0.6f;
        this.alpha = life < 0.7f ? 1.0f : 1.0f - (life - 0.7f) / 0.3f;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = Math.min(1.0f, (this.age + partialTick) / this.lifetime);
        return (this.size0 + (this.size1 - this.size0) * life) * 0.5f;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
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
            return new FireParticle(level, x, y, z, vx, vy, vz, 1.0f, 2.0f, 12, 0.9f, this.set);
        }
    }
}
