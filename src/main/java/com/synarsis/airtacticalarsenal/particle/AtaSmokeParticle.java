package com.synarsis.airtacticalarsenal.particle;

import com.synarsis.airtacticalarsenal.client.fx.FxCurves;
import com.synarsis.airtacticalarsenal.client.fx.FxGlow;
import com.synarsis.airtacticalarsenal.client.fx.FxView;
import com.synarsis.airtacticalarsenal.client.fx.SmokeMotion;
import com.synarsis.airtacticalarsenal.client.fx.SmokeParams;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AtaSmokeParticle extends AtaParticle {

    public static SpriteSet sprites;

    private final SmokeParams params;
    private final SmokeMotion motion;
    private final float spin;
    private final float[] rgb = new float[3];
    private double lit;

    public AtaSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            SmokeParams params, SpriteSet set) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.params = params;
        this.motion = new SmokeMotion(params, vx, vy, vz, x, z, this.random.nextDouble(),
                this.random.nextDouble() * Math.PI * 2.0);
        this.lifetime = Math.max(1, params.lifetime());
        this.rCol = params.red();
        this.gCol = params.green();
        this.bCol = params.blue();
        this.alpha = 0.0f;
        this.quadSize = params.size0() * 0.5f;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        this.spin = (this.random.nextFloat() - 0.5f) * 2.0f * params.spin();
        int first = params.pixel() ? 4 : 0;
        this.setSprite(set.get(first + this.random.nextInt(4), 7));
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
        this.roll += this.spin;

        double[] wind = FxView.wind(this.level, this.y);
        this.motion.accelerate(wind[0], wind[1], this.x, this.z);

        double nx = this.x + this.motion.vx;
        double ny = this.y + this.motion.vy;
        double nz = this.z + this.motion.vz;
        if (this.params.collide() && this.solidAt(nx, ny, nz)) {
            this.motion.vy = 0.0;
            ny = this.y + 0.1;
            if (this.solidAt(nx, ny, nz)) {
                this.motion.vx *= 0.3;
                this.motion.vz *= 0.3;
                nx = this.x;
                nz = this.z;
            }
        }
        this.xd = this.motion.vx;
        this.yd = this.motion.vy;
        this.zd = this.motion.vz;
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.setPos(this.x, this.y, this.z);

        float t = (float) this.age / this.lifetime;
        this.rgb[0] = this.params.red() + (this.params.endRed() - this.params.red()) * t;
        this.rgb[1] = this.params.green() + (this.params.endGreen() - this.params.green()) * t;
        this.rgb[2] = this.params.blue() + (this.params.endBlue() - this.params.blue()) * t;
        this.lit = FxGlow.tint(this.x, this.y, this.z, this.rgb);
        this.rCol = this.rgb[0];
        this.gCol = this.rgb[1];
        this.bCol = this.rgb[2];
        this.alpha = (float) (this.params.alpha() * FxCurves.smokeAlpha(this.age, this.lifetime));
    }

    @Override
    public float getQuadSize(float partialTick) {
        return (float) FxCurves.smokeSize(this.params.size0(), this.params.size1(), this.age + partialTick,
                this.params.growStart(), this.params.growDuration()) * 0.5f;
    }

    @Override
    protected int getLightColor(float partialTick) {
        if (this.params.glow()) {
            return LightTexture.FULL_BRIGHT;
        }
        int base = super.getLightColor(partialTick);
        if (this.lit <= 0.02) {
            return base;
        }
        int block = Math.max(LightTexture.block(base), (int) Math.round(15.0 * Math.min(1.0, this.lit * 1.5)));
        return LightTexture.pack(block, LightTexture.sky(base));
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<AtaSmokeOptions> {
        private final SpriteSet set;

        public Provider(SpriteSet set) {
            this.set = set;
            sprites = set;
        }

        @Override
        public Particle createParticle(AtaSmokeOptions options, ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz) {
            return new AtaSmokeParticle(level, x, y, z, vx, vy, vz, options.params(), this.set);
        }
    }
}
