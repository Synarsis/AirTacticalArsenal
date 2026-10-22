package com.synarsis.airtacticalarsenal.client.fx.light;

public final class BeamMath {
    private BeamMath() {
    }
    public static double hg(double cosine, double asymmetry) {
        return (1.0 - asymmetry * asymmetry) / (4.0 * Math.PI
                * Math.pow(1.0 + asymmetry * asymmetry - 2.0 * asymmetry * cosine, 1.5));
    }
    public static double phase(double cosine) {
        return 0.8 * hg(cosine, 0.7) + 0.2 * hg(cosine, -0.3);
    }
    public static double density(double rain, double thunder, double fogEnd, boolean snow) {
        return 0.002 + 0.03 * rain * (snow ? 1.5 : 1.0) + 0.02 * thunder + 1.5 / Math.max(fogEnd, 16.0);
    }
    public static double integrate(double start, double end, double jitter, double density, double cosine,
            java.util.function.DoubleUnaryOperator irradiance) {
        if (end <= start) return 0;
        double stepLength = (end - start) / 16;
        double sum = 0;
        for (int index = 0; index < 16; index++) {
            double distance = start + (index + jitter) * stepLength;
            sum += irradiance.applyAsDouble(distance) * density * phase(cosine)
                    * stepLength * Math.exp(-density * distance);
        }
        return sum;
    }
    public static double bilateral(double centreDepth, double neighborDepth) {
        return Math.exp(-Math.abs(centreDepth - neighborDepth) * 2.0);
    }
}
