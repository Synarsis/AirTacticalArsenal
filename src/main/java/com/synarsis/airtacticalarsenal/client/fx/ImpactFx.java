package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import com.synarsis.airtacticalarsenal.client.fx.light.HazeSources;
import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ImpactFx {

    private static final int PILLAR_FROM = 2;
    private static final int PILLAR_TICKS = 10;
    private static final int PILLAR_PUFFS = 45;
    private static final int EMBER_TICKS = 1200;
    private static final int EMBER_FLAME_TICKS = 200;
    private static final int WRECK_TICKS = 600;
    private static final int FIREBALL_TICKS = 70;
    private static final int FIREBALL_LIGHT_TICKS = 140;
    private static final int PILLAR_LIGHT_TICKS = 100;
    private static final int FIREBALL_HAZE_TICKS = 60;
    public static final int SPARKS_BLAST = 60;
    public static final int SPARKS_WRECK = 20;

    private ImpactFx() {
    }

    public static void blast(Vec3 pos, double power, boolean cap) {
        blast(pos, power, cap, SPARKS_BLAST);
    }

    public static void blast(Vec3 pos, double power, boolean cap, int sparks) {
        ClientLevel level = FxSpawn.level();
        if (level == null) {
            return;
        }
        FxCameraShake.add(pos, power);
        flash(pos, power);
        if (!FxView.particlesWanted()) {
            return;
        }

        double ground = LaunchFx.groundBelow(level, pos.add(0.0, 1.0, 0.0));
        float[] colour = FxPalette.cloudColour(LaunchFx.groundKind(level, pos.add(0.0, 1.0, 0.0)));
        BlockState under = level.getBlockState(BlockPos.containing(pos.x, ground - 0.5, pos.z));
        double size = FxBudget.sizeMultiplier(FxView.distanceTo(pos));
        double scale = Math.sqrt(power);

        fireball(level, pos, power, scale, size);
        sparks(level, pos, power, sparks);
        FxSpawn.ring(level, pos.x, ground + 0.15, pos.z, 2.0f, (float) (60.0 * power), 12);
        dustRing(level, pos, ground, colour, power, size);
        thrownEarth(level, pos, ground, under, power);
        pillar(level, pos, ground, power, scale, cap);
        embers(pos, ground, power);
    }

    public static void flash(Vec3 pos, double power) {
        ClientLevel level = FxSpawn.level();
        if (level == null) {
            return;
        }
        FxScreenFlash.flash(pos.add(0.0, 1.5, 0.0), power);
        blastLight(pos, power);
        FxSpawn.flash(level, pos.x, pos.y + 1.5, pos.z, (float) (30.0 * power), 5);
        if (FxView.allow(1, pos, true) > 0) {
            FxSpawn.glow(level, pos.x, pos.y + 1.5, pos.z, (float) (75.0 * power), 5, 0.35f, 1.0f, 0.88f, 0.70f, false);
            var ball = FxSpawn.glow(level, pos.x, pos.y + 2.0, pos.z, (float) (20.0 * Math.sqrt(power)), FIREBALL_TICKS,
                    0.3f, 1.0f, 0.55f, 0.20f, true);
            if (ball != null) {
                ball.rise(0.05);
            }
        }
    }

    private static void blastLight(Vec3 pos, double power) {
        double reach = Math.sqrt(power);
        AtaLights.point(pos.add(0.0, 2.0, 0.0)).colour(1.0f, 0.90f, 0.75f).strength(16.0 * power, 96.0 * reach)
                .life(1, 0, 10, Fall.EXPONENTIAL).shadows(true);
        AtaLights.point(pos.add(0.0, 2.0, 0.0)).colour(1.0f, 0.55f, 0.20f).strength(9.0 * power, 60.0 * reach)
                .radiusEnd(0.6).life(1, 0, FIREBALL_LIGHT_TICKS - 1, Fall.LINEAR).flicker(0.20, 9.0).wrap(0.5)
                .drift(new Vec3(0.0, 0.05, 0.0)).seed(pos.hashCode()).shadows(true);
        AtaLights.point(pos.add(0.0, 8.0, 0.0)).colour(1.0f, 0.55f, 0.25f).strength(3.0 * power, 24.0 * reach)
                .life(1, 0, PILLAR_LIGHT_TICKS - 1, Fall.LINEAR).flicker(0.15, 1.5).seed(pos.hashCode() * 31 + 7);
        HazeSources.at(pos.add(0.0, 2.0, 0.0), 10.0 * reach, 1.0, FIREBALL_HAZE_TICKS);
    }

    private static void fireball(ClientLevel level, Vec3 pos, double power, double scale, double size) {
        int n = FxView.allow((int) Math.round(40.0 * power), pos, false);
        for (int i = 0; i < n; i++) {
            double u = FxView.random() * 2.0 - 1.0;
            double a = FxView.random() * Math.PI * 2.0;
            double r = Math.sqrt(1.0 - u * u);
            Vec3 dir = new Vec3(r * Math.cos(a), u * 0.7, r * Math.sin(a));
            double speed = 0.6 + FxView.random() * 0.8;
            FxSpawn.fire(level, pos.x + dir.x * 0.5, pos.y + 0.8 + dir.y * 0.5, pos.z + dir.z * 0.5,
                    dir.x * speed, dir.y * speed, dir.z * speed, (float) (3.0 * scale * size), (float) (8.0 * scale * size),
                    25 + (int) (FxView.random() * 20), 0.85f);
        }
    }

    private static void sparks(ClientLevel level, Vec3 pos, double power, int count) {
        int n = FxView.allow((int) Math.round(count * power), pos, false);
        for (int i = 0; i < n; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double up = 0.2 + FxView.random() * 0.8;
            double flat = Math.sqrt(1.0 - up * up);
            double speed = 0.8 + FxView.random() * 0.8;
            FxSpawn.spark(level, pos.x, pos.y + 0.8, pos.z, Math.cos(a) * flat * speed, up * speed,
                    Math.sin(a) * flat * speed, 0.3f, 10 + (int) (FxView.random() * 15), 0.03f);
        }
    }

    private static void dustRing(ClientLevel level, Vec3 pos, double ground, float[] colour, double power, double size) {
        int n = FxView.allow((int) Math.round(80.0 * power), pos, false);
        for (int i = 0; i < n; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double speed = 1.0 + FxView.random() * 0.7;
            float shade = (float) (0.92 + FxView.random() * 0.14);
            boolean fine = FxView.random() < 0.5;
            SmokeParams p = SmokePresets.dustRing(colour, shade, fine, 200 + (int) (FxView.random() * 100)).scaled(size);
            FxSpawn.smoke(level, p, pos.x + Math.cos(a), ground + 0.3 + FxView.random() * 0.7, pos.z + Math.sin(a),
                    Math.cos(a) * speed, 0.0, Math.sin(a) * speed);
        }
    }

    private static void thrownEarth(ClientLevel level, Vec3 pos, double ground, BlockState under, double power) {
        int chips = FxView.allow((int) Math.round(50.0 * power), pos, false);
        for (int i = 0; i < chips; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double speed = 0.6 + FxView.random() * 0.8;
            FxSpawn.blockDust(level, under, pos.x, ground + 0.5, pos.z, Math.cos(a) * speed, 0.4 + FxView.random() * 0.6,
                    Math.sin(a) * speed);
        }
        int pieces = FxView.allow((int) Math.round(12.0 * power), pos, false);
        for (int i = 0; i < pieces; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double speed = 0.5 + FxView.random() * 0.7;
            FxSpawn.debris(level, pos.x, ground + 0.8, pos.z, Math.cos(a) * speed, 0.5 + FxView.random() * 0.6,
                    Math.sin(a) * speed, 100 + (int) (FxView.random() * 60));
        }
    }

    private static void pillar(ClientLevel level, Vec3 pos, double ground, double power, double scale, boolean cap) {
        double rise = cap ? 1.0 : 0.5;
        FxScheduler.later(PILLAR_FROM, () -> FxScheduler.during(PILLAR_TICKS, tick -> {
            int n = FxView.allow((int) Math.round(PILLAR_PUFFS * power), pos, true);
            double size = FxBudget.sizeMultiplier(FxView.distanceTo(pos));
            for (int i = 0; i < n; i++) {
                SmokeParams p = SmokePresets.pillar(cap).withSize((float) (4.0 * scale), (float) (7.0 * scale))
                        .scaled(size);
                double yd = (0.18 + FxView.random() * 0.18) * rise;
                FxSpawn.smoke(level, p, pos.x + jitter(2.0 * scale), ground + 0.5 + FxView.random() * 2.0,
                        pos.z + jitter(2.0 * scale), jitter(0.05), yd, jitter(0.05));
            }
        }));
    }

    private static void embers(Vec3 pos, double ground, double power) {
        int count = 8 + (int) (FxView.random() * 5);
        double radius = 3.0 + 3.0 * power;
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double r = Math.sqrt(FxView.random()) * radius;
            Vec3 at = new Vec3(pos.x + Math.cos(a) * r, ground + 0.1, pos.z + Math.sin(a) * r);
            spots.add(at);
            int[] age = {0};
            int offset = (int) (FxView.random() * 10);
            FxScheduler.run(() -> {
                ClientLevel level = FxSpawn.level();
                if (level == null) {
                    return false;
                }
                int t = age[0]++;
                if ((t + offset) % 10 == 0 && FxView.allow(1, at, false) > 0) {
                    FxSpawn.smoke(level, SmokePresets.emberSmoke(), at.x + jitter(0.2), at.y, at.z + jitter(0.2), 0.0, 0.02, 0.0);
                    if (t < EMBER_FLAME_TICKS) {
                        FxSpawn.fire(level, at.x, at.y, at.z, 0.0, 0.03, 0.0, 0.7f, 0.3f, 6 + (int) (FxView.random() * 4), 0.9f);
                    }
                }
                if (FxView.random() < 1.0 / 20.0) {
                    FxSpawn.wink(level, at.x + jitter(0.2), at.y + 0.05, at.z + jitter(0.2), 0.16f);
                }
                return t < EMBER_TICKS;
            });
        }
        int lights = Math.min(3, count);
        for (int k = 0; k < lights; k++) {
            double x = 0.0;
            double z = 0.0;
            int n = 0;
            for (int i = k; i < spots.size(); i += lights) {
                x += spots.get(i).x;
                z += spots.get(i).z;
                n++;
            }
            AtaLights.point(new Vec3(x / n, ground + 0.6, z / n)).colour(1.0f, 0.35f, 0.10f).strength(1.2, 10.0)
                    .life(0, EMBER_TICKS - 60, 60, Fall.LINEAR).flicker(0.3, 1.5).seed(k * 31 + count);
        }
    }

    public static void telDestroyed(IskanderTelEntity tel, boolean detonated) {
        Vec3 centre = new Vec3(tel.getX(), tel.getY() + 2.0, tel.getZ());
        if (!com.synarsis.airtacticalarsenal.compat.ExplosionOverhaulCompat.leadsVisuals()) {
            blast(centre, 0.6, false, SPARKS_WRECK);
            if (detonated) {
                blast(TelGeometry.localToWorld(tel.getX(), tel.getY(), tel.getZ(), tel.getYRot(), tel.getXRot(),
                        tel.roll, new Vec3(0.0, 3.4, -3.5)), 0.8, true);
            }
        }
        Vec3 engine = TelGeometry.localToWorld(tel.getX(), tel.getY(), tel.getZ(), tel.getYRot(), tel.getXRot(), tel.roll,
                new Vec3(0.0, 3.6, 3.7));
        Vec3 bay = TelGeometry.localToWorld(tel.getX(), tel.getY(), tel.getZ(), tel.getYRot(), tel.getXRot(), tel.roll,
                new Vec3(0.0, 3.4, -3.5));
        wreck(engine);
        wreck(bay);
    }

    public static void patriotDestroyed(com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity vehicle,
            boolean detonated) {
        Vec3 tractor = TelGeometry.localToWorld(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(),
                vehicle.getXRot(), vehicle.roll, new Vec3(0.0, 2.5, 11.0));
        Vec3 trailer = TelGeometry.localToWorld(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(),
                vehicle.getXRot(), vehicle.roll, new Vec3(0.0, 3.0, -1.0));
        if (!com.synarsis.airtacticalarsenal.compat.ExplosionOverhaulCompat.leadsVisuals()) {
            blast(tractor, 0.6, false, SPARKS_WRECK);
            blast(trailer, 0.8, true);
        }
        wreck(tractor);
        wreck(trailer);
    }

    public static void wreck(Vec3 at) {
        AtaLights.point(at.add(0.0, 0.5, 0.0)).colour(1.0f, 0.50f, 0.20f).strength(4.0, 20.0).flicker(0.25, 7.0)
                .life(0, WRECK_TICKS - 60, 60, Fall.LINEAR).seed(at.hashCode()).shadows(true);
        HazeSources.at(at.add(0.0, 1.0, 0.0), 4.0, 0.7, WRECK_TICKS);
        int[] age = {0};
        FxScheduler.run(() -> {
            ClientLevel level = FxSpawn.level();
            if (level == null || !FxView.particlesWanted()) {
                return false;
            }
            int t = age[0]++;
            double fade = 1.0 - (double) t / WRECK_TICKS;
            int puffs = FxView.allow(fade > 0.4 ? 2 : 1, at, false);
            for (int i = 0; i < puffs; i++) {
                boolean fine = FxView.random() < 0.25;
                FxSpawn.smoke(level, SmokePresets.wreck(fine, 180 + (int) (FxView.random() * 80)), at.x + jitter(0.8), at.y,
                        at.z + jitter(0.8), jitter(0.03), 0.06, jitter(0.03));
            }
            if (t % 3 == 0 && fade > 0.3 && FxView.allow(1, at, false) > 0) {
                FxSpawn.fire(level, at.x + jitter(0.6), at.y - 0.2, at.z + jitter(0.6), 0.0, 0.05, 0.0, 1.1f, 0.5f,
                        6 + (int) (FxView.random() * 5), 0.9f);
            }
            return t < WRECK_TICKS;
        });
    }

    private static double jitter(double amount) {
        return (FxView.random() - 0.5) * 2.0 * amount;
    }
}
