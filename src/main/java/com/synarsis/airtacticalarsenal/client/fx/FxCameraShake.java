package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class FxCameraShake {

    private static final double FORGET_SECONDS = 6.0;
    private static final double LIMIT = 8.0;

    private record Shake(long start, double amplitude, double[] phase) {
    }

    private static final List<Shake> SHAKES = new ArrayList<>();

    private FxCameraShake() {
    }

    public static void add(Vec3 origin, double power) {
        if (!AtaClientConfig.isFxCameraShakeEnabled()) {
            return;
        }
        double distance = FxView.distanceTo(origin);
        double amplitude = FxCurves.shakeAmplitude(power, distance);
        if (amplitude < 0.05) {
            return;
        }
        double[] phase = new double[6];
        for (int i = 0; i < phase.length; i++) {
            phase[i] = FxView.random() * Math.PI * 2.0;
        }
        SHAKES.add(new Shake(FxScheduler.now() + FxCurves.shockDelayTicks(distance), amplitude, phase));
    }

    static double noise(double seconds, double p0, double p1, double p2) {
        return (Math.sin(seconds * 17.0 + p0) + 0.6 * Math.sin(seconds * 29.0 + p1) + 0.4 * Math.sin(seconds * 43.0 + p2))
                / 2.0;
    }

    public static double[] offsetAt(double nowTicks) {
        double yaw = 0.0;
        double pitch = 0.0;
        double roll = 0.0;
        for (Shake s : SHAKES) {
            double seconds = (nowTicks - s.start()) / 20.0;
            if (seconds < 0.0) {
                continue;
            }
            double a = FxCurves.shakeAt(s.amplitude(), seconds);
            yaw += a * noise(seconds, s.phase()[0], s.phase()[1], s.phase()[2]);
            pitch += a * noise(seconds + 0.37, s.phase()[3], s.phase()[4], s.phase()[5]);
            roll += a * 0.5 * noise(seconds + 0.81, s.phase()[2], s.phase()[0], s.phase()[4]);
        }
        return new double[] {Mth.clamp(yaw, -LIMIT, LIMIT), Mth.clamp(pitch, -LIMIT, LIMIT),
                Mth.clamp(roll, -LIMIT, LIMIT)};
    }

    public static void tick() {
        long now = FxScheduler.now();
        SHAKES.removeIf(s -> (now - s.start()) / 20.0 > FORGET_SECONDS);
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty() || !AtaClientConfig.isFxCameraShakeEnabled()) {
            return;
        }
        double[] o = offsetAt(FxScheduler.now() + event.getPartialTick());
        event.setYaw((float) (event.getYaw() + o[0]));
        event.setPitch((float) (event.getPitch() + o[1]));
        event.setRoll((float) (event.getRoll() + o[2]));
    }

    public static void clear() {
        SHAKES.clear();
    }
}
