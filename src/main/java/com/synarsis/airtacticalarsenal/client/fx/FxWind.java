package com.synarsis.airtacticalarsenal.client.fx;

public final class FxWind {

    public static final double TURN_PERIOD_TICKS = 12000.0;
    public static final double MIN_SPEED = 0.01;
    public static final double MAX_SPEED = 0.04;

    private FxWind() {
    }

    public static long seedOf(String name) {
        long h = 1125899906842597L;
        for (int i = 0; i < name.length(); i++) {
            h = 31 * h + name.charAt(i);
        }
        return h;
    }

    public static double[] compute(long time, long seed, double y) {
        double phase = ((seed >>> 3) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        double wander = ((seed >>> 19) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        double angle = phase + time / TURN_PERIOD_TICKS * Math.PI * 2.0;
        double mix = 0.5 + 0.5 * Math.sin(time / 1700.0 + wander);
        double speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * mix;
        double height = 1.0 + Math.max(0.0, y) / 128.0;
        return new double[] {Math.cos(angle) * speed * height, Math.sin(angle) * speed * height};
    }
}
