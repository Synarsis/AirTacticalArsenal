package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.client.fx.FxScheduler;
import com.synarsis.airtacticalarsenal.client.fx.FxSpawn;
import com.synarsis.airtacticalarsenal.client.fx.FxView;
import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

@OnlyIn(Dist.CLIENT)
public final class HeadlightFx {

    private static final double[][] HEADLAMPS = {{1.25, 1.61, 7.82}, {-1.25, 1.61, 7.82}};
    private static final double[][] TAIL_LAMPS = {{1.9, 1.2, -7.2}, {-1.9, 1.2, -7.2}};
    private static final double BEAM_DOWN = 6.0;
    private static final double HALO_RANGE = 96.0;
    private static final float HEAD_HALO = 0.6f;
    private static final float TAIL_HALO = 0.25f;
    private static final double HIDDEN_SLACK = 0.6;

    static final int LEASE_TICKS = 2;

    interface Mount {
        Vec3 world(double[] local, float partialTick);

        Vec3 beam();
    }

    private static final class Set {
        final AtaLights.Light[] heads = new AtaLights.Light[2];
        final AtaLights.Light[] tails = new AtaLights.Light[2];
        long lastTick;
        BooleanSupplier gone;
    }

    private static final Map<Integer, Set> LIT = new HashMap<>();

    private HeadlightFx() {
    }

    public static void clear() {
        for (Set s : LIT.values()) {
            putOut(s);
        }
        LIT.clear();
    }

    public static void tick(IskanderTelEntity tel) {
        boolean on = !tel.isRemoved() && tel.areHeadlightsOn();
        int id = tel.getId();
        if (!on) {
            Set set = LIT.remove(id);
            if (set != null) {
                putOut(set);
            }
            return;
        }
        keep(id, FxScheduler.now(), tel::isRemoved, new Mount() {
            @Override
            public Vec3 world(double[] local, float partialTick) {
                return HeadlightFx.world(tel, local, partialTick);
            }

            @Override
            public Vec3 beam() {
                return HeadlightFx.beam(tel);
            }
        });
        halos(tel);
    }

    static void keep(int id, long now, BooleanSupplier gone, Mount mount) {
        Set set = LIT.get(id);
        if (set == null) {
            set = new Set();
            BooleanSupplier stillThere = () -> !gone.getAsBoolean();
            for (int i = 0; i < 2; i++) {
                final double[] head = HEADLAMPS[i];
                set.heads[i] = AtaLights.spot(mount.world(head, 1.0f)).following(p -> mount.world(head, p), stillThere)
                        .colour(1.0f, 0.92f, 0.78f).strength(3.5, 36.0).life(0, -1, 0, Fall.LINEAR).shadows(true)
                        .cone(mount.beam(), 18.0, 32.0).tintsParticles(false);
                final double[] tail = TAIL_LAMPS[i];
                set.tails[i] = AtaLights.point(mount.world(tail, 1.0f)).following(p -> mount.world(tail, p), stillThere)
                        .colour(1.0f, 0.10f, 0.05f).strength(0.5, 3.0).life(0, -1, 0, Fall.LINEAR)
                        .tintsParticles(false);
            }
            LIT.put(id, set);
        }
        set.lastTick = now;
        set.gone = gone;
        Vec3 axis = mount.beam();
        for (AtaLights.Light head : set.heads) {
            head.setDirection(axis);
        }
    }

    public static void sweep(long now) {
        Iterator<Set> it = LIT.values().iterator();
        while (it.hasNext()) {
            Set set = it.next();
            if (set.gone.getAsBoolean() || now - set.lastTick >= LEASE_TICKS || now < set.lastTick) {
                putOut(set);
                it.remove();
            }
        }
    }

    static boolean lit(int id) {
        return LIT.containsKey(id);
    }

    private static void putOut(Set set) {
        for (AtaLights.Light l : set.heads) {
            if (l != null) {
                l.kill();
            }
        }
        for (AtaLights.Light l : set.tails) {
            if (l != null) {
                l.kill();
            }
        }
    }

    private static Vec3 world(IskanderTelEntity tel, double[] local, float partialTick) {
        double x = Mth.lerp(partialTick, tel.xo, tel.getX());
        double y = Mth.lerp(partialTick, tel.yo, tel.getY());
        double z = Mth.lerp(partialTick, tel.zo, tel.getZ());
        float yaw = Mth.rotLerp(partialTick, tel.yRotO, tel.getYRot());
        float pitch = Mth.lerp(partialTick, tel.xRotO, tel.getXRot());
        float roll = Mth.lerp(partialTick, tel.rollO, tel.roll);
        return TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, new Vec3(local[0], local[1], local[2]));
    }

    private static Vec3 beam(IskanderTelEntity tel) {
        double down = Math.toRadians(BEAM_DOWN);
        return TelGeometry.rotateToWorld(tel.getYRot(), tel.getXRot(), tel.roll,
                new Vec3(0.0, -Math.sin(down), Math.cos(down)));
    }

    private static void halos(IskanderTelEntity tel) {
        ClientLevel level = FxSpawn.level();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null || !FxView.particlesWanted()
                || FxView.distanceTo(tel.position()) > HALO_RANGE) {
            return;
        }
        Vec3 eye = FxView.camera();
        for (double[] lamp : HEADLAMPS) {
            halo(level, mc, eye, world(tel, lamp, 1.0f), HEAD_HALO);
        }
        for (double[] lamp : TAIL_LAMPS) {
            halo(level, mc, eye, world(tel, lamp, 1.0f), TAIL_HALO);
        }
    }

    private static void halo(ClientLevel level, Minecraft mc, Vec3 eye, Vec3 at, float size) {
        HitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        boolean seen = hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(at) < HIDDEN_SLACK;
        if (seen) {
            FxSpawn.flash(level, at.x, at.y, at.z, size, 2);
        }
    }
}
