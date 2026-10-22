package com.synarsis.airtacticalarsenal.client.fx.light;

public final class BeamPattern {
    private BeamPattern() {
    }

    private static double smooth(double start, double end, double value) {
        double ratio = Math.clamp((value - start) / (end - start), 0.0, 1.0);
        return ratio * ratio * (3.0 - 2.0 * ratio);
    }

    public static double low(double azimuth, double elevation) {
        double cut = -0.6 + Math.clamp(azimuth, 0.0, 6.0) * 0.27;
        double below = cut - elevation;
        double horizontal = 0.95 * Math.exp(-Math.pow((azimuth - 2.0) / 14.0, 2.0))
                + 0.05 * (1.0 - smooth(45.0, 65.0, Math.abs(azimuth)));
        double vertical = below < 1.5 ? 0.35 + 0.65 * Math.max(below, 0.0) / 1.5
                : Math.max(Math.exp(-(below - 1.5) / 6.0), 0.02 * (1.0 - smooth(35.0, 50.0, below)));
        double blend = smooth(-0.8, 0.0, below);
        return horizontal * (0.02 + (vertical - 0.02) * blend);
    }

    public static double high(double azimuth, double elevation) {
        double horizontal = 0.9 * Math.exp(-Math.pow(azimuth / 8.0, 2.0))
                + 0.1 * (1.0 - smooth(25.0, 40.0, Math.abs(azimuth)));
        double vertical = 0.9 * Math.exp(-Math.pow(elevation / 4.0, 2.0))
                + 0.1 * (1.0 - smooth(15.0, 30.0, Math.abs(elevation)));
        return horizontal * vertical;
    }

    public static double pattern(int mode, double right, double up, double forward) {
        if (forward <= 0.0 || mode == 0) {
            return 0.0;
        }
        double azimuth = Math.toDegrees(Math.atan2(right, forward));
        double elevation = Math.toDegrees(Math.atan2(up, forward));
        double angle = Math.toDegrees(Math.atan2(Math.hypot(right, up), forward));
        return Math.max(mode == 2 ? high(azimuth, elevation) : low(azimuth, elevation),
                0.01 * (1.0 - smooth(60.0, 80.0, angle)));
    }

    public static double irradiance(double intensity, double pattern, double distance, double radius) {
        double window = Math.clamp(1.0 - Math.pow(distance / radius, 4.0), 0.0, 1.0);
        return intensity * pattern / Math.max(distance * distance, 0.35 * 0.35) * window * window;
    }
}
