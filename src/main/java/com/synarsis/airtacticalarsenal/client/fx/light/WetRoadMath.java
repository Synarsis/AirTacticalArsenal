package com.synarsis.airtacticalarsenal.client.fx.light;

public final class WetRoadMath {
    private WetRoadMath() {
    }
    public static boolean open(double[] cells) {
        for (double opacity : cells) if (opacity > 0.0) return false;
        return true;
    }
    public static double specular(double normalUp, double normalHalf, double viewHalf,
            double rain, double irradiance, boolean open) {
        if (normalUp <= 0.7 || !open || rain <= 0.0) return 0.0;
        double fresnel = 0.02 + 0.98 * Math.pow(1.0 - Math.clamp(viewHalf, 0.0, 1.0), 5.0);
        return 0.6 * rain * irradiance * Math.pow(Math.max(normalHalf, 0.0), 60.0) * fresnel;
    }
}
