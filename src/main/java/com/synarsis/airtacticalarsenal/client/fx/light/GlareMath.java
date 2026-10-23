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

    public static final double LAMP_RADIUS = 0.25;
    public static final double DEPTH_MARGIN = 0.3;
    public static final double SMOOTHING_SECONDS = 0.08;
    public static final int SAMPLES = 9;
    public static final double EXPOSURE_FLOOR = 0.4;
    public static final double EXPOSURE_GAIN = 0.25;

    public static double target(double eye) {
        return Math.max(EXPOSURE_FLOOR, 1.0 / (1.0 + EXPOSURE_GAIN * eye));
    }

    public static double[][] samples(double radiusPixels) {
        double[][] points = new double[SAMPLES][];
        points[0] = new double[] {0.0, 0.0};
        for (int index = 1; index < SAMPLES; index++) {
            double angle = 2.0 * Math.PI * (index - 1) / (SAMPLES - 1);
            points[index] = new double[] {radiusPixels * Math.cos(angle), radiusPixels * Math.sin(angle)};
        }
        return points;
    }

    public static double lampRadiusPixels(double lampDistance, double projectionScale, double screenHeight) {
        return Math.min(12.0, Math.max(1.5, LAMP_RADIUS * projectionScale / lampDistance * 0.5 * screenHeight));
    }

    public static boolean sampleSees(double sceneDistance, double lampDistance) {
        return sceneDistance >= lampDistance - DEPTH_MARGIN;
    }

    public static double smoothSeen(double previous, double now, double seconds) {
        return previous + (now - previous) * (1.0 - Math.exp(-Math.max(0.0, seconds) / SMOOTHING_SECONDS));
    }

    public static float previousSlot(java.util.Map<Integer, Integer> slots, int lightId) {
        Integer slot = slots.get(lightId);
        return slot == null ? -1.0f : slot.floatValue();
    }

    public static double adapt(double previous, double target, double seconds) {
        double duration = target < previous ? 0.3 : 1.5;
        return previous + (target - previous) * (1.0 - Math.exp(-Math.max(0.0, seconds) / duration));
    }

    public static double halo(double eye) {
        return 1.0 + 3.0 * Math.sqrt(Math.min(eye / 5.0, 4.0));
    }
}
