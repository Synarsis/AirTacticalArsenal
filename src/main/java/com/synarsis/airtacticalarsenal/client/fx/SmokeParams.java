package com.synarsis.airtacticalarsenal.client.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SmokeParams(float red, float green, float blue, float endRed, float endGreen, float endBlue,
        float size0, float size1, int lifetime, int growStart, int growDuration, float drag, float rise, int riseDelay,
        float wind, float alpha, float capPush, float spin, boolean glow, boolean collide, boolean pixel) {

    private record Look(float red, float green, float blue, float endRed, float endGreen, float endBlue, float alpha,
            boolean glow, boolean pixel) {
        static final MapCodec<Look> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("r").forGetter(Look::red),
                Codec.FLOAT.fieldOf("g").forGetter(Look::green),
                Codec.FLOAT.fieldOf("b").forGetter(Look::blue),
                Codec.FLOAT.fieldOf("r1").forGetter(Look::endRed),
                Codec.FLOAT.fieldOf("g1").forGetter(Look::endGreen),
                Codec.FLOAT.fieldOf("b1").forGetter(Look::endBlue),
                Codec.FLOAT.fieldOf("alpha").forGetter(Look::alpha),
                Codec.BOOL.fieldOf("glow").forGetter(Look::glow),
                Codec.BOOL.fieldOf("pixel").forGetter(Look::pixel)).apply(i, Look::new));
    }

    private record Motion(float size0, float size1, int lifetime, int growStart, int growDuration, float drag,
            float rise, int riseDelay, float wind, float capPush, float spin, boolean collide) {
        static final MapCodec<Motion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("size0").forGetter(Motion::size0),
                Codec.FLOAT.fieldOf("size1").forGetter(Motion::size1),
                Codec.INT.fieldOf("lifetime").forGetter(Motion::lifetime),
                Codec.INT.fieldOf("grow_start").forGetter(Motion::growStart),
                Codec.INT.fieldOf("grow_duration").forGetter(Motion::growDuration),
                Codec.FLOAT.fieldOf("drag").forGetter(Motion::drag),
                Codec.FLOAT.fieldOf("rise").forGetter(Motion::rise),
                Codec.INT.fieldOf("rise_delay").forGetter(Motion::riseDelay),
                Codec.FLOAT.fieldOf("wind").forGetter(Motion::wind),
                Codec.FLOAT.fieldOf("cap_push").forGetter(Motion::capPush),
                Codec.FLOAT.fieldOf("spin").forGetter(Motion::spin),
                Codec.BOOL.fieldOf("collide").forGetter(Motion::collide)).apply(i, Motion::new));
    }

    public static final MapCodec<SmokeParams> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Look.CODEC.fieldOf("look").forGetter(SmokeParams::look),
            Motion.CODEC.fieldOf("motion").forGetter(SmokeParams::motion)).apply(i, SmokeParams::of));

    public static final StreamCodec<ByteBuf, SmokeParams> STREAM_CODEC = StreamCodec.of(SmokeParams::write,
            SmokeParams::read);

    public static SmokeParams soft(float red, float green, float blue) {
        return new SmokeParams(red, green, blue, red, green, blue, 1.0f, 4.0f, 200, 0, 100, 0.96f, 0.0f, 0, 1.0f, 1.0f,
                0.0f, 0.01f, false, true, false);
    }

    public float buoyancy() {
        if (this.rise <= 0.0f) {
            return 0.0f;
        }
        double ticks = Math.max(1, this.lifetime - this.riseDelay);
        double d = this.drag;
        if (d >= 0.9999) {
            return (float) (2.0 * this.rise / (ticks * ticks));
        }
        double settling = d * (1.0 - Math.pow(d, ticks)) / (1.0 - d);
        double effective = Math.max(1.0, ticks - settling);
        return (float) (this.rise * (1.0 - d) / effective);
    }

    private Look look() {
        return new Look(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue, this.alpha,
                this.glow, this.pixel);
    }

    private Motion motion() {
        return new Motion(this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag,
                this.rise, this.riseDelay, this.wind, this.capPush, this.spin, this.collide);
    }

    private static SmokeParams of(Look l, Motion m) {
        return new SmokeParams(l.red, l.green, l.blue, l.endRed, l.endGreen, l.endBlue, m.size0, m.size1,
                m.lifetime, m.growStart, m.growDuration, m.drag, m.rise, m.riseDelay, m.wind, l.alpha, m.capPush,
                m.spin, l.glow, m.collide, l.pixel);
    }

    public SmokeParams withColour(float r, float g, float b) {
        return this.withColours(r, g, b, this.endRed, this.endGreen, this.endBlue);
    }

    public SmokeParams withColours(float r, float g, float b, float r1, float g1, float b1) {
        return new SmokeParams(r, g, b, r1, g1, b1, this.size0, this.size1, this.lifetime, this.growStart,
                this.growDuration, this.drag, this.rise, this.riseDelay, this.wind, this.alpha, this.capPush,
                this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withSize(float s0, float s1) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue, s0, s1,
                this.lifetime, this.growStart, this.growDuration, this.drag, this.rise, this.riseDelay, this.wind,
                this.alpha, this.capPush, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withLife(int ticks) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, ticks, this.growStart, this.growDuration, this.drag, this.rise,
                this.riseDelay, this.wind, this.alpha, this.capPush, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withGrowth(int start, int duration) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, start, duration, this.drag, this.rise, this.riseDelay,
                this.wind, this.alpha, this.capPush, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withDrift(float drag, float rise, float wind) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, drag, rise,
                this.riseDelay, wind, this.alpha, this.capPush, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withRiseDelay(int ticks) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag, this.rise,
                ticks, this.wind, this.alpha, this.capPush, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withAlpha(float a) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag, this.rise,
                this.riseDelay, this.wind, a, this.capPush, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withCap(float push) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag, this.rise,
                this.riseDelay, this.wind, this.alpha, push, this.spin, this.glow, this.collide, this.pixel);
    }

    public SmokeParams withGlow(boolean on) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag, this.rise,
                this.riseDelay, this.wind, this.alpha, this.capPush, this.spin, on, this.collide, this.pixel);
    }

    public SmokeParams withCollide(boolean on) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag, this.rise,
                this.riseDelay, this.wind, this.alpha, this.capPush, this.spin, this.glow, on, this.pixel);
    }

    public SmokeParams withPixel(boolean on) {
        return new SmokeParams(this.red, this.green, this.blue, this.endRed, this.endGreen, this.endBlue,
                this.size0, this.size1, this.lifetime, this.growStart, this.growDuration, this.drag, this.rise,
                this.riseDelay, this.wind, this.alpha, this.capPush, this.spin, this.glow, this.collide, on);
    }

    public SmokeParams scaled(double factor) {
        return this.withSize((float) (this.size0 * factor), (float) (this.size1 * factor));
    }

    private static void write(ByteBuf buf, SmokeParams p) {
        ByteBufCodecs.FLOAT.encode(buf, p.red);
        ByteBufCodecs.FLOAT.encode(buf, p.green);
        ByteBufCodecs.FLOAT.encode(buf, p.blue);
        ByteBufCodecs.FLOAT.encode(buf, p.endRed);
        ByteBufCodecs.FLOAT.encode(buf, p.endGreen);
        ByteBufCodecs.FLOAT.encode(buf, p.endBlue);
        ByteBufCodecs.FLOAT.encode(buf, p.size0);
        ByteBufCodecs.FLOAT.encode(buf, p.size1);
        ByteBufCodecs.VAR_INT.encode(buf, p.lifetime);
        ByteBufCodecs.VAR_INT.encode(buf, p.growStart);
        ByteBufCodecs.VAR_INT.encode(buf, p.growDuration);
        ByteBufCodecs.FLOAT.encode(buf, p.drag);
        ByteBufCodecs.FLOAT.encode(buf, p.rise);
        ByteBufCodecs.VAR_INT.encode(buf, p.riseDelay);
        ByteBufCodecs.FLOAT.encode(buf, p.wind);
        ByteBufCodecs.FLOAT.encode(buf, p.alpha);
        ByteBufCodecs.FLOAT.encode(buf, p.capPush);
        ByteBufCodecs.FLOAT.encode(buf, p.spin);
        ByteBufCodecs.BOOL.encode(buf, p.glow);
        ByteBufCodecs.BOOL.encode(buf, p.collide);
        ByteBufCodecs.BOOL.encode(buf, p.pixel);
    }

    private static SmokeParams read(ByteBuf buf) {
        return new SmokeParams(ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf));
    }
}
