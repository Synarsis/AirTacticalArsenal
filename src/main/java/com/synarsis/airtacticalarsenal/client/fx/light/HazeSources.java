package com.synarsis.airtacticalarsenal.client.fx.light;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HazeSources {

    public static final class Source {
        private final AtaLights.Position position;
        private final double radius;
        private final double strength;
        private final int life;
        private int age;
        private boolean alive = true;

        private Source(AtaLights.Position position, double radius, double strength, int life) {
            this.position = position;
            this.radius = radius;
            this.strength = strength;
            this.life = life;
        }

        public Vec3 position(float partialTick) {
            return this.position.at(partialTick);
        }

        public double radius() {
            return this.radius;
        }

        public double strength(float partialTick) {
            if (!this.alive) {
                return 0.0;
            }
            if (this.life < 0) {
                return this.strength;
            }
            return this.strength * Math.max(0.0, 1.0 - (this.age + partialTick) / this.life);
        }

        public void kill() {
            this.alive = false;
        }

        public boolean alive() {
            return this.alive;
        }
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    private HazeSources() {
    }

    public static Source add(AtaLights.Position position, double radius, double strength, int life) {
        Source s = new Source(position, radius, strength, life);
        SOURCES.add(s);
        return s;
    }

    public static Source at(Vec3 where, double radius, double strength, int life) {
        return add(partial -> where, radius, strength, life);
    }

    public static void tick() {
        for (int i = SOURCES.size() - 1; i >= 0; i--) {
            Source s = SOURCES.get(i);
            s.age++;
            if (!s.alive || (s.life >= 0 && s.age >= s.life)) {
                SOURCES.remove(i);
            }
        }
    }

    public static List<Source> all() {
        return Collections.unmodifiableList(SOURCES);
    }

    public static void clear() {
        SOURCES.clear();
    }
}
