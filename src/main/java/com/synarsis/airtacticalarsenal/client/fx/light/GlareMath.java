package com.synarsis.airtacticalarsenal.client.fx.light;

public final class GlareMath {
    private GlareMath() {
    }

    public static double eye(double intensity, double pattern, double distanceSquared, double visibility) {
        return intensity * pattern / Math.max(distanceSquared, 0.35 * 0.35) * visibility;
    }

    public static double veil(double eye, double degrees, double daylight) {
        return 0.8 * eye / (degrees * degrees + 0.5 * 0.5) * (1.0 - 0.7 * daylight);
    }

    public static double target(double eye) {
        return Math.max(0.65, 1.0 / (1.0 + 0.1 * eye));
    }

    public static double adapt(double previous, double target, double seconds) {
        double duration = target < previous ? 0.3 : 1.5;
        return previous + (target - previous) * (1.0 - Math.exp(-Math.max(0.0, seconds) / duration));
    }

    public static double halo(double eye) {
        return 1.0 + 3.0 * Math.sqrt(Math.min(eye / 5.0, 4.0));
    }
}
