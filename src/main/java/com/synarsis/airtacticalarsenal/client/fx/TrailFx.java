package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import com.synarsis.airtacticalarsenal.particle.FlashParticle;
import com.synarsis.airtacticalarsenal.client.fx.light.HazeSources;
import com.synarsis.airtacticalarsenal.client.sound.IskanderWhistleSound;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class TrailFx {

    private static final double CLIMB_STEP = 0.8;
    private static final double PUFFS_PER_TICK = 8.0;
    private static final double GREY_BELOW = 100.0;
    private static final double WHITE_ABOVE = 150.0;
    private static final double GROUND_LAYER = 8.0;
    private static final int GROUND_TICKS = 80;
    private static final double WHISTLE_RANGE = 200.0;
    private static final double WHISTLE_TICKS = 80.0;
    private static final double HAZE_BELOW = 60.0;
    private static final int STREAK_TICKS = 40;

    private static final class Flight {
        final FxTrail trail = new FxTrail();
        final IskanderEntity missile;
        AtaLights.Light flare;
        HazeSources.Source haze;
        FlashParticle halo;
        FlashParticle streak;
        double baseY = Double.NaN;
        boolean whistled;
        int streakMade;

        Flight(IskanderEntity missile) {
            this.missile = missile;
        }

        void putOut() {
            if (this.flare != null) {
                this.flare.kill();
                this.flare = null;
            }
            if (this.haze != null) {
                this.haze.kill();
                this.haze = null;
            }
            if (this.halo != null) {
                this.halo.remove();
                this.halo = null;
            }
            if (this.streak != null) {
                this.streak.remove();
                this.streak = null;
            }
        }
    }

    private static final Map<Integer, Flight> FLIGHTS = new HashMap<>();

    private TrailFx() {
    }

    public static void clear() {
        for (Flight f : FLIGHTS.values()) {
            f.putOut();
        }
        FLIGHTS.clear();
    }

    public static void prune() {
        FLIGHTS.entrySet().removeIf(e -> {
            boolean gone = e.getValue().missile.isRemoved();
            if (gone) {
                e.getValue().putOut();
            }
            return gone;
        });
    }

    public static void forget(int entityId) {
        Flight f = FLIGHTS.remove(entityId);
        if (f != null) {
            f.putOut();
        }
    }

    public static void tick(IskanderEntity missile, int phase, int climbAge, Vec3 motion, Vec3 from, Vec3 to, Vec3 nose,
            BlockPos target) {
        ClientLevel level = FxSpawn.level();
        if (level == null || phase == IskanderEntity.PHASE_PREPARING) {
            return;
        }
        Flight f = FLIGHTS.computeIfAbsent(missile.getId(), id -> new Flight(missile));
        if (Double.isNaN(f.baseY)) {
            f.baseY = to.y;
        }

        boolean burning = phase == IskanderEntity.PHASE_LAUNCH || phase == IskanderEntity.PHASE_ACTIVE
                || phase == IskanderEntity.PHASE_BALLISTIC;
        double height = to.y - f.baseY;
        if (burning) {
            burn(f, to, height);
        } else {
            f.putOut();
        }

        if (!FxView.particlesWanted()) {
            return;
        }
        double speed = motion.length();
        double distance = FxView.distanceTo(to);

        if (phase == IskanderEntity.PHASE_LAUNCH) {
            climb(level, f, climbAge, from, to, motion, distance);
        } else if (phase == IskanderEntity.PHASE_ACTIVE || phase == IskanderEntity.PHASE_BALLISTIC) {
            flight(level, f, from, to, motion, speed, distance);
        } else if (phase == IskanderEntity.PHASE_TERMINAL) {
            dive(level, f, from, to, nose, motion, speed, distance, target);
        }
    }

    private static void burn(Flight f, Vec3 nozzle, double height) {
        if (f.flare == null) {
            f.flare = AtaLights.point(nozzle).colour(1.0f, 0.65f, 0.30f).strength(7.0, 56.0).flicker(0.15, 11.0)
                    .seed(f.missile.getId()).life(0, -1, 0, com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall.LINEAR)
                    .ownedBy(() -> !f.missile.isRemoved());
        }
        f.flare.shadows(height < HAZE_BELOW);
        f.flare.moveTo(nozzle);
        f.flare.setScale(FxCurves.flareScale(height));
        halos(f, nozzle);
        if (height < HAZE_BELOW) {
            if (f.haze == null) {
                f.haze = HazeSources.add(partial -> f.flare == null ? nozzle : f.flare.position(partial), 3.0, 0.8, -1);
            }
        } else if (f.haze != null) {
            f.haze.kill();
            f.haze = null;
        }
    }

    private static void halos(Flight f, Vec3 nozzle) {
        ClientLevel level = FxSpawn.level();
        if (level == null || !FxView.particlesWanted()) {
            return;
        }
        AtaLights.Light flare = f.flare;
        FlashParticle.Follower follower = new FlashParticle.Follower() {
            @Override
            public Vec3 at(float partialTick) {
                return flare.position(partialTick);
            }

            @Override
            public boolean alive() {
                return flare.alive() && !f.missile.isRemoved();
            }
        };
        if (f.halo == null && FxView.allow(1, nozzle, true) > 0) {
            f.halo = FxSpawn.glow(level, nozzle.x, nozzle.y, nozzle.z, 6.0f, Integer.MAX_VALUE / 2, 0.5f, 1.0f, 0.70f, 0.40f,
                    false);
            if (f.halo != null) {
                f.halo.follow(follower);
            }
        }
        if (f.streak == null && f.streakMade == 0 && FxView.allow(1, nozzle, true) > 0) {
            f.streak = FxSpawn.glow(level, nozzle.x, nozzle.y, nozzle.z, 6.0f, STREAK_TICKS, 0.45f, 1.0f, 0.60f, 0.30f, true);
            if (f.streak != null) {
                f.streak.follow(follower).streak(7.0f, 1.6f, 6.0f);
                f.streakMade = 1;
            }
        }
        double scale = FxCurves.flareScale(nozzle.y - f.baseY);
        if (f.halo != null) {
            f.halo.setScale(scale);
        }
    }

    private static void climb(ClientLevel level, Flight f, int climbAge, Vec3 from, Vec3 to, Vec3 motion, double distance) {
        Vec3 back = motion.lengthSqr() > 1.0E-6 ? motion.normalize().scale(-0.3) : new Vec3(0.0, -0.3, 0.0);
        if (climbAge < 60) {
            int flames = FxView.allow(2, to, true);
            for (int i = 0; i < flames; i++) {
                FxSpawn.fire(level, to.x + jitter(0.15), to.y + jitter(0.15), to.z + jitter(0.15),
                        back.x + jitter(0.05), back.y + jitter(0.05), back.z + jitter(0.05), 1.8f, 0.9f,
                        6 + (int) (FxView.random() * 5), 0.92f);
            }
        }
        double mult = lodShare(distance);
        double size = sizeShare(distance);
        boolean nearGround = climbAge < GROUND_TICKS;
        for (Vec3 p : f.trail.advance(from, to, CLIMB_STEP / mult)) {
            double above = nearGround ? heightAbove(level, p) : Double.NaN;
            if (!Double.isNaN(above) && above < GROUND_LAYER) {
                double a = FxView.random() * Math.PI * 2.0;
                double v = 0.05 + FxView.random() * 0.1;
                FxSpawn.smoke(level, SmokePresets.trailGround(0.9f).scaled(size), p.x, p.y, p.z, Math.cos(a) * v, 0.0,
                        Math.sin(a) * v);
            } else {
                FxSpawn.smoke(level, SmokePresets.trailColumn(0.93f, 400, 6.0f).scaled(size), p.x, p.y, p.z, jitter(0.03),
                        jitter(0.01), jitter(0.03));
            }
        }
    }

    private static void flight(ClientLevel level, Flight f, Vec3 from, Vec3 to, Vec3 motion, double speed,
            double distance) {
        if (FxView.allow(1, to, true) > 0) {
            Vec3 back = motion.lengthSqr() > 1.0E-6 ? motion.normalize().scale(-0.2) : Vec3.ZERO;
            FxSpawn.fire(level, to.x, to.y, to.z, back.x, back.y, back.z, 1.4f, 0.6f, 5, 0.9f);
        }
        double mult = lodShare(distance);
        double size = sizeShare(distance);
        double step = Math.max(1.0, speed / PUFFS_PER_TICK);
        for (Vec3 p : f.trail.advance(from, to, step / mult)) {
            double h = FxCurves.smoothstep((p.y - GREY_BELOW) / (WHITE_ABOVE - GREY_BELOW));
            float grey = (float) (0.62 + 0.36 * h);
            SmokeParams trail = SmokePresets.trailColumn(grey, (int) (400 + 400 * h), (float) (6.0 + 1.0 * h));
            FxSpawn.smoke(level, trail.scaled(size), p.x, p.y, p.z, jitter(0.02), 0.0, jitter(0.02));
        }
    }

    private static void dive(ClientLevel level, Flight f, Vec3 from, Vec3 to, Vec3 nose, Vec3 motion, double speed,
            double distance, BlockPos target) {
        double mult = lodShare(distance);
        double size = sizeShare(distance);
        double step = Math.max(1.0, speed / PUFFS_PER_TICK);
        for (Vec3 p : f.trail.advance(from, to, step / mult)) {
            SmokeParams streak = SmokeParams.soft(0.97f, 0.97f, 1.0f).withSize(0.6f, 1.6f).withLife(20)
                    .withGrowth(0, 20).withDrift(0.97f, 0.0f, 1.0f).withAlpha(0.7f).withCollide(false).scaled(size);
            FxSpawn.smoke(level, streak, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
        if (FxView.random() < 1.0 / 6.0 && FxView.allow(1, nose, false) > 0) {
            Vec3 drift = motion.lengthSqr() > 1.0E-6 ? motion.normalize().scale(-0.1) : Vec3.ZERO;
            FxSpawn.spark(level, nose.x, nose.y, nose.z, drift.x + jitter(0.1), drift.y + jitter(0.1),
                    drift.z + jitter(0.1), 0.25f, 10 + (int) (FxView.random() * 8), 0.02f);
        }
        whistle(f, to, speed, target);
    }

    private static void whistle(Flight f, Vec3 at, double speed, BlockPos target) {
        if (f.whistled || speed < 0.1) {
            return;
        }
        Vec3 aim = Vec3.atCenterOf(target);
        double ticksToGo = at.distanceTo(aim) / speed;
        if (ticksToGo > WHISTLE_TICKS) {
            return;
        }
        f.whistled = true;
        if (FxView.distanceTo(aim) <= WHISTLE_RANGE) {
            Minecraft.getInstance().getSoundManager().play(new IskanderWhistleSound(f.missile));
        }
    }

    private static double heightAbove(ClientLevel level, Vec3 p) {
        double ground = LaunchFx.groundWithin(level, p, (int) GROUND_LAYER + 1);
        return Double.isNaN(ground) ? Double.NaN : p.y - ground;
    }

    private static double lodShare(double distance) {
        double share = FxBudget.countMultiplier(distance, true) * FxView.quality().multiplier()
                * Math.min(1.0, FxView.share()) * FxBudget.pressure(FxBudget.live());
        return Math.max(0.05, Math.min(1.0, share));
    }

    private static double sizeShare(double distance) {
        return FxBudget.sizeMultiplier(distance) / Math.sqrt(Math.max(0.2, FxView.quality().multiplier()));
    }

    private static double jitter(double amount) {
        return (FxView.random() - 0.5) * 2.0 * amount;
    }
}
