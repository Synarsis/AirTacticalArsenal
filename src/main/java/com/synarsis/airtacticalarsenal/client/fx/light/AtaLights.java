package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class AtaLights {

    public enum Kind {
        POINT,
        SPOT
    }

    @FunctionalInterface
    public interface Position {
        Vec3 at(float partialTick);
    }

    public static final class Light {
        final int id;
        final Kind kind;
        private Vec3 previous;
        private Vec3 current;
        private Position follow;
        private BooleanSupplier owner;
        private Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        private double cosInner = 1.0;
        private double cosOuter = 1.0;
        private float red = 1.0f;
        private float green = 1.0f;
        private float blue = 1.0f;
        private double intensity = 1.0;
        private double radius = 8.0;
        private double radiusEnd = 1.0;
        private double scale = 1.0;
        private int rise;
        private int hold = -1;
        private int fall;
        private Fall fallKind = Fall.LINEAR;
        private double flickerAmount;
        private double flickerHz;
        private int seed;
        private double wrap;
        private boolean shadows;
        private boolean tint = true;
        private Vec3 drift = Vec3.ZERO;
        private int age;
        private boolean alive = true;

        private Light(int id, Kind kind, Vec3 pos) {
            this.id = id;
            this.kind = kind;
            this.previous = pos;
            this.current = pos;
            this.seed = id * 7919;
        }

        public Light colour(float r, float g, float b) {
            this.red = r;
            this.green = g;
            this.blue = b;
            return this;
        }

        public Light strength(double intensity, double radius) {
            this.intensity = intensity;
            this.radius = radius;
            return this;
        }

        public Light radiusEnd(double ratio) {
            this.radiusEnd = ratio;
            return this;
        }

        public Light life(int rise, int hold, int fall, Fall kind) {
            this.rise = Math.max(0, rise);
            this.hold = hold;
            this.fall = Math.max(0, fall);
            this.fallKind = kind;
            return this;
        }

        public Light flicker(double amount, double hz) {
            this.flickerAmount = amount;
            this.flickerHz = hz;
            return this;
        }

        public Light seed(int seed) {
            this.seed = seed;
            return this;
        }

        public Light wrap(double wrap) {
            this.wrap = Math.max(0.0, Math.min(0.6, wrap));
            return this;
        }

        public Light shadows(boolean on) {
            this.shadows = on;
            return this;
        }

        public Light tintsParticles(boolean on) {
            this.tint = on;
            return this;
        }

        public Light drift(Vec3 perTick) {
            this.drift = perTick;
            return this;
        }

        public Light cone(Vec3 axis, double innerDegrees, double outerDegrees) {
            double len = axis.length();
            this.direction = len < 1.0E-9 ? new Vec3(0.0, 0.0, 1.0) : axis.scale(1.0 / len);
            this.cosInner = Math.cos(Math.toRadians(innerDegrees));
            this.cosOuter = Math.cos(Math.toRadians(outerDegrees));
            return this;
        }

        public Light following(Position source) {
            this.follow = source;
            return this;
        }

        public Light following(Position source, BooleanSupplier stillThere) {
            this.follow = source;
            this.owner = stillThere;
            return this;
        }

        public Light ownedBy(BooleanSupplier stillThere) {
            this.owner = stillThere;
            return this;
        }

        public void moveTo(Vec3 where) {
            this.previous = this.current;
            this.current = where;
        }

        public void setScale(double scale) {
            this.scale = Math.max(0.0, scale);
        }

        public void setDirection(Vec3 axis) {
            double len = axis.length();
            if (len > 1.0E-9) {
                this.direction = axis.scale(1.0 / len);
            }
        }

        public void kill() {
            this.alive = false;
        }

        public boolean alive() {
            return this.alive;
        }

        public int age() {
            return this.age;
        }

        public Kind kind() {
            return this.kind;
        }

        public Vec3 position(float partialTick) {
            if (this.follow != null) {
                return this.follow.at(partialTick);
            }
            return this.previous.lerp(this.current, partialTick);
        }

        public Vec3 direction() {
            return this.direction;
        }

        public double cosInner() {
            return this.cosInner;
        }

        public double cosOuter() {
            return this.cosOuter;
        }

        public float red() {
            return this.red;
        }

        public float green() {
            return this.green;
        }

        public float blue() {
            return this.blue;
        }

        public double wrap() {
            return this.wrap;
        }

        public boolean shadows() {
            return this.shadows;
        }

        public boolean tintsParticles() {
            return this.tint;
        }

        public int lifetime() {
            return LightCurves.lifetime(this.rise, this.hold, this.fall);
        }

        public double strength(float partialTick) {
            if (!this.alive) {
                return 0.0;
            }
            double t = this.age + partialTick;
            return this.intensity * this.scale * LightCurves.envelope(t, this.rise, this.hold, this.fall, this.fallKind)
                    * LightCurves.flicker(this.seed, t, this.flickerHz, this.flickerAmount);
        }

        public double radius(float partialTick) {
            int life = this.lifetime();
            double progress = life <= 0 ? 0.0 : Math.min(1.0, (this.age + partialTick) / life);
            return this.radius * this.scale * (1.0 + (this.radiusEnd - 1.0) * progress);
        }
    }

    private static final List<Light> LIGHTS = new ArrayList<>();
    private static int nextId = 1;

    private AtaLights() {
    }

    public static Light point(Vec3 pos) {
        return add(new Light(nextId++, Kind.POINT, pos));
    }

    public static Light spot(Vec3 pos) {
        return add(new Light(nextId++, Kind.SPOT, pos));
    }

    private static Light add(Light light) {
        LIGHTS.add(light);
        return light;
    }

    public static Light flash(Vec3 pos, float r, float g, float b, double intensity, double radius, int fallTicks) {
        return point(pos).colour(r, g, b).strength(intensity, radius).life(1, 0, fallTicks, Fall.EXPONENTIAL);
    }

    public static Light attach(Position source, float r, float g, float b, double intensity, double radius) {
        return point(source.at(1.0f)).following(source).colour(r, g, b).strength(intensity, radius)
                .life(0, -1, 0, Fall.LINEAR);
    }

    public static void remove(Light light) {
        light.kill();
    }

    public static void tick() {
        for (int i = LIGHTS.size() - 1; i >= 0; i--) {
            Light l = LIGHTS.get(i);
            l.age++;
            if (l.drift != Vec3.ZERO) {
                l.moveTo(l.current.add(l.drift));
            }
            if (l.alive && l.owner != null && !l.owner.getAsBoolean()) {
                l.alive = false;
            }
            int life = l.lifetime();
            if (!l.alive || (life >= 0 && l.age >= life)) {
                LIGHTS.remove(i);
            }
        }
    }

    public static List<Light> all() {
        return Collections.unmodifiableList(LIGHTS);
    }

    public static boolean isEmpty() {
        return LIGHTS.isEmpty();
    }

    public static void clear() {
        LIGHTS.clear();
    }

    public static List<LightSelect.Candidate> candidates(float partialTick) {
        List<LightSelect.Candidate> out = new ArrayList<>(LIGHTS.size());
        for (Light l : LIGHTS) {
            double strength = l.strength(partialTick);
            if (strength > 1.0E-3) {
                Vec3 p = l.position(partialTick);
                out.add(new LightSelect.Candidate(l.id, p.x, p.y, p.z, l.radius(partialTick), strength));
            }
        }
        return out;
    }

    public static Light byId(int id) {
        for (Light l : LIGHTS) {
            if (l.id == id) {
                return l;
            }
        }
        return null;
    }
}
