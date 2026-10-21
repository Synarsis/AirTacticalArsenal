package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import com.synarsis.airtacticalarsenal.client.fx.light.HazeSources;
import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class LaunchFx {

    private static final int LATE_TICKS = 40;
    private static final int HAZE_TICKS = 80;

    private static final class State {
        final LaunchTrigger[] triggers = {new LaunchTrigger(), new LaunchTrigger()};
        long watchUntil;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Set<Integer> DRAWN = new HashSet<>();

    private LaunchFx() {
    }

    public static void clear() {
        STATES.clear();
        DRAWN.clear();
    }

    public static void tick(IskanderTelEntity tel) {
        ClientLevel level = FxSpawn.level();
        if (level == null || tel.isRemoved()) {
            STATES.remove(tel.getId());
            return;
        }
        if (STATES.size() > 32) {
            STATES.values().removeIf(s -> s.watchUntil < level.getGameTime() - 600);
        }
        State st = STATES.computeIfAbsent(tel.getId(), id -> new State());
        long now = level.getGameTime();
        boolean counting = tel.getTelState() == TelState.COUNTDOWN;

        for (int slot = 0; slot < 2; slot++) {
            int left = counting ? tel.getCountdownLeft(slot) : -1;
            if (left >= 0) {
                st.watchUntil = now + 80;
                if (left <= IskanderTelEntity.PREPARING_TICKS) {
                    countdown(level, tel, slot, left, now);
                }
            }
            if (st.triggers[slot].observe(left, tel.isLoaded(slot), now)) {
                ignite(level, tel, slot);
            }
        }

        if (st.watchUntil > now) {
            for (int slot = 0; slot < 2; slot++) {
                if (st.triggers[slot].recent(now)) {
                    continue;
                }
                Vec3 foot = tel.footOf(slot);
                for (IskanderEntity missile : level.getEntitiesOfClass(IskanderEntity.class,
                        new AABB(foot, foot).inflate(6.0, 8.0, 6.0))) {
                    if (missile.tickCount <= 4 && !DRAWN.contains(missile.getId())) {
                        if (st.triggers[slot].missileSeen(now)) {
                            ignite(level, tel, slot);
                        }
                        break;
                    }
                }
            }
        }
    }

    private static void countdown(ClientLevel level, IskanderTelEntity tel, int slot, int left, long now) {
        Vec3 foot = tel.footOf(slot);
        boolean late = left <= LATE_TICKS;
        if (!FxView.particlesWanted()) {
            return;
        }
        int puffs = late ? (now % 4 != 3 ? 1 : 0) : ((now + slot) % 4 == 0 ? 1 : 0);
        puffs = FxView.allow(puffs, foot, false);
        for (int i = 0; i < puffs; i++) {
            FxSpawn.smoke(level, SmokePresets.steam(late), foot.x + jitter(0.4), foot.y + 0.1 + FxView.random() * 0.4,
                    foot.z + jitter(0.4), jitter(0.03), 0.02, jitter(0.03));
        }
        if (!late) {
            return;
        }
        if (now % 3 == 0 && FxView.allow(1, foot, false) > 0) {
            FxSpawn.spark(level, foot.x + jitter(0.3), foot.y + 0.1, foot.z + jitter(0.3), jitter(0.25), 0.15 + FxView.random() * 0.2,
                    jitter(0.25), 0.22f, 12 + (int) (FxView.random() * 10), 0.03f);
        }
        int dust = FxView.allow(2, foot, false);
        double ground = groundBelow(level, foot);
        float[] colour = FxPalette.cloudColour(groundKind(level, foot));
        for (int i = 0; i < dust; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double speed = 0.15 + FxView.random() * 0.2;
            FxSpawn.smoke(level, SmokePresets.creepingDust(colour), foot.x + Math.cos(a) * 0.6, ground + 0.2,
                    foot.z + Math.sin(a) * 0.6, Math.cos(a) * speed, 0.0, Math.sin(a) * speed);
        }
    }

    private static void ignite(ClientLevel level, IskanderTelEntity tel, int slot) {
        Vec3 foot = tel.footOf(slot);
        for (IskanderEntity missile : level.getEntitiesOfClass(IskanderEntity.class,
                new AABB(foot, foot).inflate(8.0, 10.0, 8.0))) {
            DRAWN.add(missile.getId());
        }
        if (DRAWN.size() > 256) {
            DRAWN.clear();
        }
        ignition(level, foot);
    }

    public static void ignition(ClientLevel level, Vec3 foot) {
        FxCameraShake.add(foot, 0.6);
        FxScreenFlash.flash(foot.add(0.0, 1.5, 0.0), 0.5);
        AtaLights.point(foot.add(0.0, 1.0, 0.0)).colour(1.0f, 0.75f, 0.45f).strength(10.0, 56.0)
                .life(1, 0, 20, Fall.EXPONENTIAL).shadows(true);
        AtaLights.point(foot.add(0.0, 2.0, 0.0)).colour(1.0f, 0.55f, 0.25f).strength(4.0, 36.0)
                .life(0, 0, 100, Fall.LINEAR);
        HazeSources.at(foot.add(0.0, 1.0, 0.0), 6.0, 1.0, HAZE_TICKS);
        FxSpawn.flash(level, foot.x, foot.y + 0.5, foot.z, 10.0f, 4);
        if (FxView.allow(1, foot, true) > 0) {
            FxSpawn.glow(level, foot.x, foot.y + 0.5, foot.z, 25.0f, 4, 0.35f, 1.0f, 0.80f, 0.55f, false);
        }
        if (!FxView.particlesWanted()) {
            return;
        }

        double ground = groundBelow(level, foot);
        FxPalette.Ground kind = groundKind(level, foot);
        float[] colour = FxPalette.cloudColour(kind);
        double distance = FxView.distanceTo(foot);
        double size = FxBudget.sizeMultiplier(distance);

        int cloud = FxView.allow(100 + (int) (FxView.random() * 41), foot, false);
        for (int i = 0; i < cloud; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double speed = 0.5 + FxView.random() * 0.8;
            boolean fine = FxView.random() < 0.25;
            float shade = (float) (0.94 + FxView.random() * 0.12);
            SmokeParams p = SmokePresets.launchCloud(colour, shade, fine, 300 + (int) (FxView.random() * 200)).scaled(size);
            FxSpawn.smoke(level, p, foot.x + Math.cos(a) * 0.5, ground + FxView.random() * 1.2, foot.z + Math.sin(a) * 0.5,
                    Math.cos(a) * speed, 0.0, Math.sin(a) * speed);
        }

        FxScheduler.during(15, tick -> {
            int tongues = FxView.allow(3, foot, false);
            for (int i = 0; i < tongues; i++) {
                double a = FxView.random() * Math.PI * 2.0;
                double speed = 0.5 + FxView.random() * 0.6;
                FxSpawn.fire(level, foot.x + Math.cos(a) * 0.5, ground + 0.4, foot.z + Math.sin(a) * 0.5,
                        Math.cos(a) * speed, 0.02 + FxView.random() * 0.04, Math.sin(a) * speed,
                        (float) (1.5 * size), (float) (3.5 * size), 8 + (int) (FxView.random() * 7), 0.88f);
            }
        });

        BlockState under = level.getBlockState(BlockPos.containing(foot.x, ground - 0.5, foot.z));
        int chips = FxView.allow(40, foot, false);
        for (int i = 0; i < chips; i++) {
            double a = FxView.random() * Math.PI * 2.0;
            double speed = 0.3 + FxView.random() * 0.5;
            FxSpawn.blockDust(level, under, foot.x + Math.cos(a) * 0.8, ground + 0.3, foot.z + Math.sin(a) * 0.8,
                    Math.cos(a) * speed, 0.3 + FxView.random() * 0.4, Math.sin(a) * speed);
        }
    }

    public static double groundBelow(ClientLevel level, Vec3 point) {
        double found = groundWithin(level, point, 8);
        return Double.isNaN(found) ? point.y - 3.0 : found;
    }

    public static double groundWithin(ClientLevel level, Vec3 point, int blocks) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int top = (int) Math.floor(point.y);
        for (int dy = 0; dy <= blocks; dy++) {
            probe.set((int) Math.floor(point.x), top - dy, (int) Math.floor(point.z));
            if (level.isOutsideBuildHeight(probe.getY())) {
                break;
            }
            BlockState state = level.getBlockState(probe);
            if (!state.getCollisionShape(level, probe).isEmpty()) {
                return probe.getY() + 1.0;
            }
        }
        return Double.NaN;
    }

    public static FxPalette.Ground groundKind(ClientLevel level, Vec3 point) {
        double ground = groundBelow(level, point);
        BlockState state = level.getBlockState(BlockPos.containing(point.x, ground - 0.5, point.z));
        return FxPalette.kindOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    private static double jitter(double amount) {
        return (FxView.random() - 0.5) * 2.0 * amount;
    }
}
