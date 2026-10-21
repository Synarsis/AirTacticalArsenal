package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class Units {

    public static final double TICKS_PER_SECOND = 20.0;
    public static final double KMH_PER_MS = 3.6;

    private Units() {
    }

    public static double kmhToMs(double kmh) {
        return kmh / KMH_PER_MS;
    }

    public static double msToKmh(double ms) {
        return ms * KMH_PER_MS;
    }

    public static double blocksPerTickToMs(double perTick) {
        return perTick * TICKS_PER_SECOND;
    }

    public static double msToBlocksPerTick(double ms) {
        return ms / TICKS_PER_SECOND;
    }

    public static double rpmToRadPerSecond(double rpm) {
        return rpm * 2.0 * Math.PI / 60.0;
    }

    public static double radPerSecondToRpm(double rad) {
        return rad * 60.0 / (2.0 * Math.PI);
    }
}
