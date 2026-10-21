package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.config.FxQuality;

public final class FxBudget {

    public static final int CAP = 2500;
    public static final int ESSENTIAL_ROOM = 500;

    private static int live;

    private FxBudget() {
    }

    public static void born() {
        live++;
    }

    public static void died() {
        if (live > 0) {
            live--;
        }
    }

    public static int live() {
        return live;
    }

    public static void reset() {
        live = 0;
    }

    public static double countMultiplier(double distance) {
        if (distance < 64.0) {
            return 1.0;
        }
        if (distance < 192.0) {
            return 0.4;
        }
        if (distance < 512.0) {
            return 0.15;
        }
        return 0.0;
    }

    public static double sizeMultiplier(double distance) {
        if (distance < 64.0) {
            return 1.0;
        }
        if (distance < 192.0) {
            return 1.6;
        }
        if (distance < 512.0) {
            return 2.5;
        }
        return 3.0;
    }

    public static double countMultiplier(double distance, boolean essential) {
        double m = countMultiplier(distance);
        return m == 0.0 && essential ? 0.05 : m;
    }

    public static double vanillaShare(int status) {
        return status <= 0 ? 1.0 : (status == 1 ? 0.5 : 0.2);
    }

    public static double pressure(int alive) {
        double crowded = (double) alive / CAP;
        if (crowded <= 0.7) {
            return 1.0;
        }
        return Math.max(0.1, 1.0 - (crowded - 0.7) / 0.3 * 0.9);
    }

    public static int allow(int requested, double distance, boolean essential, FxQuality quality,
            double serverShare, int alive, double roll) {
        if (requested <= 0) {
            return 0;
        }
        int ceiling = essential ? CAP + ESSENTIAL_ROOM : CAP;
        if (alive >= ceiling) {
            return 0;
        }
        double share = countMultiplier(distance, essential) * quality.multiplier() * Math.max(0.0, serverShare);
        int count = (int) Math.floor(requested * share + roll);
        return Math.max(0, Math.min(count, ceiling - alive));
    }
}
