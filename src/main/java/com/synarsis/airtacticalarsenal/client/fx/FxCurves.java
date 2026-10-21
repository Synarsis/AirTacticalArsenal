package com.synarsis.airtacticalarsenal.client.fx;

public final class FxCurves {

    public static final int FADE_IN_TICKS = 3;
    public static final double FADE_OUT_SHARE = 0.3;
    public static final double SHAKE_DECAY_SECONDS = 0.6;
    public static final double SHAKE_MAX_DEGREES = 6.0;
    public static final double SOUND_SPEED = 17.0;
    public static final int FLASH_TICKS = 8;

    private FxCurves() {
    }

    public static double clamp01(double x) {
        return x < 0.0 ? 0.0 : Math.min(x, 1.0);
    }

    public static double smoothstep(double x) {
        double t = clamp01(x);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double smokeSize(double s0, double s1, double age, double growStart, double growDuration) {
        double progress = growDuration <= 0.0 ? 1.0 : (age - growStart) / growDuration;
        return s0 + (s1 - s0) * smoothstep(progress);
    }

    public static double smokeAlpha(double age, double lifetime) {
        double in = clamp01(age / FADE_IN_TICKS);
        double fadeStart = lifetime * (1.0 - FADE_OUT_SHARE);
        double out = age <= fadeStart ? 1.0 : 1.0 - clamp01((age - fadeStart) / Math.max(1.0, lifetime - fadeStart));
        return in * out;
    }

    public static double shakeAmplitude(double power, double distance) {
        return Math.max(0.0, Math.min(SHAKE_MAX_DEGREES, power * SHAKE_MAX_DEGREES / (1.0 + distance / 24.0)));
    }

    public static double shakeAt(double a, double seconds) {
        return seconds < 0.0 ? 0.0 : a * Math.exp(-seconds / SHAKE_DECAY_SECONDS);
    }

    public static int shockDelayTicks(double distance) {
        return (int) Math.max(0.0, distance / SOUND_SPEED);
    }

    public static double flashStrength(double power, boolean lineOfSight, double cosToView, double distance) {
        if (!lineOfSight) {
            return 0.0;
        }
        double facing = (1.0 + Math.max(-1.0, Math.min(1.0, cosToView))) / 2.0;
        double ratio = distance / 80.0;
        return power * facing / (1.0 + ratio * ratio);
    }

    public static float[] fireColour(double life) {
        double t = clamp01(life);
        double[][] stops = {{1.0, 1.0, 0.85}, {1.0, 0.75, 0.2}, {1.0, 0.4, 0.08}, {0.45, 0.08, 0.04}};
        double[] at = {0.0, 0.25, 0.6, 1.0};
        for (int i = 0; i + 1 < at.length; i++) {
            if (t <= at[i + 1]) {
                double k = (t - at[i]) / (at[i + 1] - at[i]);
                return new float[] {(float) (stops[i][0] + (stops[i + 1][0] - stops[i][0]) * k),
                        (float) (stops[i][1] + (stops[i + 1][1] - stops[i][1]) * k),
                        (float) (stops[i][2] + (stops[i + 1][2] - stops[i][2]) * k)};
            }
        }
        return new float[] {(float) stops[3][0], (float) stops[3][1], (float) stops[3][2]};
    }

    public static double flareScale(double heightAboveGround) {
        return 1.0 - 0.7 * clamp01((heightAboveGround - 20.0) / 100.0);
    }

    public static int fireFrame(int age, int lifetime, int frames) {
        if (frames <= 1 || lifetime <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(frames - 1, age * frames / lifetime));
    }
}
