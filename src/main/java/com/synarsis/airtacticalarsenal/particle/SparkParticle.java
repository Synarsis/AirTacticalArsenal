package com.synarsis.airtacticalarsenal.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class SparkParticle extends AtaParticle {

    public static SpriteSet sprites;

    private static final double STREAK_FROM_SPEED = 0.03;

    private final float size;
    private final float pull;

    public SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            float size, int lifetime, float pull, SpriteSet set) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.size = size;
        this.pull = pull;
        this.lifetime = Math.max(1, lifetime);
        this.quadSize = size * 0.5f;
        this.rCol = 1.0f;
        this.gCol = 0.92f;
        this.bCol = 0.72f;
        this.pickSprite(set);
    }

    public static SparkParticle wink(ClientLevel level, double x, double y, double z, float size, SpriteSet set) {
        return new SparkParticle(level, x, y, z, 0.0, 0.0, 0.0, size, 3, 0.0f, set);
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
        this.yd -= this.pull;
        this.xd *= 0.98;
        this.yd *= 0.98;
        this.zd *= 0.98;
        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;
        this.setPos(this.x, this.y, this.z);
        float life = (float) this.age / this.lifetime;
        float flicker = 0.8f + 0.2f * this.random.nextFloat();
        this.alpha = flicker * (life < 0.5f ? 1.0f : 1.0f - (life - 0.5f) * 2.0f);
        this.gCol = 0.92f - 0.35f * life;
        this.bCol = 0.72f - 0.55f * life;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        double speed = Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        if (speed < STREAK_FROM_SPEED) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z);

        float length = (float) Mth.clamp(speed * 3.0, 0.2, 2.5);
        Vector3f along = new Vector3f((float) (this.xd / speed), (float) (this.yd / speed), (float) (this.zd / speed));
        Vector3f across = new Vector3f(px, py, pz).cross(along);
        if (across.lengthSquared() < 1.0E-8f) {
            across.set(0.0f, 1.0f, 0.0f).cross(along);
        }
        across.normalize().mul(Math.max(0.04f, this.size * 0.5f));
        Vector3f half = new Vector3f(along).mul(length * 0.5f);

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);
        this.vertex(buffer, px - half.x + across.x, py - half.y + across.y, pz - half.z + across.z, u1, v1, light);
        this.vertex(buffer, px + half.x + across.x, py + half.y + across.y, pz + half.z + across.z, u1, v0, light);
        this.vertex(buffer, px + half.x - across.x, py + half.y - across.y, pz + half.z - across.z, u0, v0, light);
        this.vertex(buffer, px - half.x - across.x, py - half.y - across.y, pz - half.z - across.z, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
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
            return new SparkParticle(level, x, y, z, vx, vy, vz, 0.3f, 18, 0.03f, this.set);
        }
    }
}
