package com.synarsis.airtacticalarsenal.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class ShockRingParticle extends AtaParticle {

    public static SpriteSet sprites;

    private static final Quaternionf FACE_UP = new Quaternionf().rotateX((float) (-Math.PI / 2.0));
    private static final Quaternionf FACE_DOWN = new Quaternionf().rotateX((float) (Math.PI / 2.0));

    private final float size0;
    private final float size1;

    public ShockRingParticle(ClientLevel level, double x, double y, double z, float size0, float size1, int lifetime,
            SpriteSet set) {
        super(level, x, y, z);
        this.size0 = size0;
        this.size1 = size1;
        this.lifetime = Math.max(1, lifetime);
        this.quadSize = size0 * 0.5f;
        this.rCol = 1.0f;
        this.gCol = 0.98f;
        this.bCol = 0.94f;
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
        float life = (float) this.age / this.lifetime;
        this.alpha = (float) Math.pow(1.0f - life, 1.5);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = Math.min(1.0f, (this.age + partialTick) / this.lifetime);
        float spread = 1.0f - (1.0f - life) * (1.0f - life);
        return (this.size0 + (this.size1 - this.size0) * spread) * 0.5f;
    }

    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf ignored, float x, float y, float z,
            float partialTicks) {
        super.renderRotatedQuad(buffer, FACE_UP, x, y, z, partialTicks);
        super.renderRotatedQuad(buffer, FACE_DOWN, x, y, z, partialTicks);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return TRANSLUCENT_NO_DEPTH;
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
            return new ShockRingParticle(level, x, y, z, 2.0f, 30.0f, 12, this.set);
        }
    }
}
