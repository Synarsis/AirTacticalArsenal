package com.synarsis.airtacticalarsenal.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FlashParticle extends AtaParticle {

    public static SpriteSet sprites;

    public interface Follower {
        Vec3 at(float partialTick);

        boolean alive();
    }

    private final float size;
    private float peakAlpha = 1.0f;
    private boolean halo;
    private boolean linearFade;
    private float scale = 1.0f;
    private Follower follower;
    private float streakHalfLength;
    private float streakHalfWidth;
    private float streakBack;
    private Vec3 axis = new Vec3(0.0, 1.0, 0.0);
    private Vec3 lastPlace;
    private double climb;

    public FlashParticle(ClientLevel level, double x, double y, double z, float size, int lifetime, SpriteSet set) {
        super(level, x, y, z);
        this.size = size;
        this.lifetime = Math.max(1, lifetime);
        this.alpha = 1.0f;
        this.rCol = 1.0f;
        this.gCol = 1.0f;
        this.bCol = 1.0f;
        this.quadSize = size * 0.5f;
        this.pickSprite(set);
    }

    public FlashParticle glow(float peak, float red, float green, float blue, boolean linear) {
        this.halo = true;
        this.peakAlpha = peak;
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
        this.linearFade = linear;
        this.alpha = peak;
        return this;
    }

    public FlashParticle follow(Follower what) {
        this.follower = what;
        this.lastPlace = what.at(1.0f);
        return this;
    }

    public FlashParticle streak(float halfLength, float halfWidth, float back) {
        this.streakHalfLength = halfLength;
        this.streakHalfWidth = halfWidth;
        this.streakBack = back;
        return this;
    }

    public FlashParticle rise(double perTick) {
        this.climb = perTick;
        return this;
    }

    public void setScale(double scale) {
        this.scale = (float) Math.max(0.0, scale);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ADDITIVE;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime || (this.follower != null && !this.follower.alive())) {
            this.remove();
            return;
        }
        this.y += this.climb;
        float life = (float) this.age / this.lifetime;
        float fade = this.halo && this.linearFade ? 1.0f - life : 1.0f - life * life;
        this.alpha = this.peakAlpha * fade;
        if (this.follower != null) {
            Vec3 now = this.follower.at(1.0f);
            Vec3 moved = now.subtract(this.lastPlace);
            if (moved.lengthSqr() > 1.0E-4) {
                this.axis = moved.normalize();
            }
            this.lastPlace = now;
            this.x = now.x;
            this.y = now.y;
            this.z = now.z;
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(float partialTick) {
        double reach = Math.max(Math.max(2.0, this.getQuadSize(partialTick) * 2.0),
                (this.streakHalfLength + this.streakBack) * this.scale * 1.5);
        return new net.minecraft.world.phys.AABB(this.x - reach, this.y - reach, this.z - reach, this.x + reach,
                this.y + reach, this.z + reach);
    }

    @Override
    public float getQuadSize(float partialTick) {
        if (this.halo) {
            return this.size * 0.5f * this.scale;
        }
        float t = this.age + partialTick;
        float pulse = 1.0f + 0.15f * (float) Math.sin(t * 1.7);
        return this.size * 0.5f * pulse * (1.0f - 0.35f * t / this.lifetime);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (this.follower != null) {
            Vec3 at = this.follower.at(partialTicks);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.xo = at.x;
            this.yo = at.y;
            this.zo = at.z;
        }
        if (this.streakHalfLength > 0.0f) {
            renderStreak(buffer, camera, partialTicks);
            return;
        }
        super.render(buffer, camera, partialTicks);
    }

    private void renderStreak(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        Vec3 centre = new Vec3(this.x, this.y, this.z).subtract(this.axis.scale(this.streakBack * this.scale));
        Vec3 toCentre = centre.subtract(cam);
        Vec3 side = toCentre.cross(this.axis);
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(this.streakHalfWidth * this.scale);
        Vec3 along = this.axis.scale(this.streakHalfLength * this.scale);
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);
        Vec3 c = centre.subtract(cam);
        corner(buffer, c.subtract(along).subtract(side), u1, v1, light);
        corner(buffer, c.subtract(along).add(side), u1, v0, light);
        corner(buffer, c.add(along).add(side), u0, v0, light);
        corner(buffer, c.add(along).subtract(side), u0, v1, light);
    }

    private void corner(VertexConsumer buffer, Vec3 p, float u, float v, int light) {
        buffer.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
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
            return new FlashParticle(level, x, y, z, 4.0f, 5, this.set);
        }
    }
}
