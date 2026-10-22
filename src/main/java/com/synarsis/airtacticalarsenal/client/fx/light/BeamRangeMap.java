package com.synarsis.airtacticalarsenal.client.fx.light;

import net.minecraft.world.phys.Vec3;

public final class BeamRangeMap {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 8;
    public static final int SAMPLES = WIDTH * HEIGHT;
    @FunctionalInterface
    public interface Trace {
        double distance(Vec3 direction, double maximum);
    }
    private BeamRangeMap() {
    }
    public static float[] build(int mode, double radius, Trace trace) {
        float[] ranges = new float[SAMPLES];
        double horizontal = mode == 2 ? 30.0 : 65.0;
        double bottom = mode == 2 ? -30.0 : -50.0;
        double top = mode == 2 ? 30.0 : 5.0;
        for (int vertical = 0; vertical < HEIGHT; vertical++) {
            double elevation = Math.toRadians(bottom + (vertical + 0.5) / HEIGHT * (top - bottom));
            for (int across = 0; across < WIDTH; across++) {
                double azimuth = Math.toRadians(-horizontal + (across + 0.5) / WIDTH * 2.0 * horizontal);
                Vec3 direction = new Vec3(Math.tan(azimuth), Math.tan(elevation), 1.0).normalize();
                ranges[vertical * WIDTH + across] = (float) Math.clamp(trace.distance(direction, radius), 0.0, radius);
            }
        }
        return ranges;
    }
}
