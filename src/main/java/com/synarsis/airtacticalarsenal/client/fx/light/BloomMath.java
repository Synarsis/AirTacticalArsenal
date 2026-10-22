package com.synarsis.airtacticalarsenal.client.fx.light;

public final class BloomMath {
    private BloomMath() {
    }
    public static double excess(double scene, double addition) {
        double excess = Math.max(scene + addition - 1.0, 0.0);
        return excess / (1.0 + excess);
    }
    public static double down(double centre, double first, double second, double third, double fourth) {
        return (4 * centre + first + second + third + fourth) / 8;
    }
    public static double up(double[] samples) {
        if (samples.length != 8) throw new IllegalArgumentException("Eight filter samples required");
        double sum = 0;
        for (int index = 0; index < samples.length; index++) sum += samples[index] * (index % 2 == 0 ? 1 : 2);
        return sum / 12;
    }
    public static int divisor(int level) {
        return 1 << Math.min(level + 1, 4);
    }
}
