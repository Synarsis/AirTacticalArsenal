package com.synarsis.airtacticalarsenal.client.fx.light;

public final class LightCurves {

    public enum Fall {
        LINEAR,
        EXPONENTIAL
    }

    private static final double DECAY = 4.0;

    private LightCurves() {
    }

    public static int lifetime(int rise, int hold, int fall) {
        return hold < 0 ? -1 : rise + hold + fall;
    }

    public static double envelope(double age, int rise, int hold, int fall, Fall kind) {
        if (age < 0.0) {
            return 0.0;
        }
        if (rise > 0 && age < rise) {
            return age / rise;
        }
        if (hold < 0 || age < rise + hold) {
            return 1.0;
        }
        if (fall <= 0) {
            return 0.0;
        }
        double t = (age - rise - hold) / fall;
        if (t >= 1.0) {
            return 0.0;
        }
        if (kind == Fall.LINEAR) {
            return 1.0 - t;
        }
        double floor = Math.exp(-DECAY);
        return (Math.exp(-DECAY * t) - floor) / (1.0 - floor);
    }

    public static double flicker(int seed, double ticks, double hz, double amount) {
        if (amount == 0.0 || hz == 0.0) {
            return 1.0;
        }
        double p1 = fraction(seed * 0.6180339887498949) * Math.PI * 2.0;
        double p2 = fraction(seed * 0.7548776662466927 + 0.31) * Math.PI * 2.0;
        double t = ticks / 20.0;
        double noise = (Math.sin(Math.PI * 2.0 * hz * t + p1) + 0.5 * Math.sin(Math.PI * 2.0 * hz * 2.3 * t + p2)) / 1.5;
        return 1.0 + amount * noise;
    }

    private static double fraction(double x) {
        return x - Math.floor(x);
    }
}
