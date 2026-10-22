package com.synarsis.airtacticalarsenal.entity.tel;

public final class TelHeadlights {

    private TelHeadlights() {
    }

    public static boolean works(float health, float maxHealth, boolean destroyed, float burningFraction) {
        return !destroyed && health > 0.0f && health > maxHealth * burningFraction;
    }

    public static byte mode(int stored) {
        return stored >= 0 && stored <= 2 ? (byte) stored : 0;
    }

    public static byte next(int current, boolean works) {
        return works ? (byte) ((mode(current) + 1) % 3) : 0;
    }

    public static String modeKey(int mode) {
        return switch (mode(mode)) {
            case 1 -> "gui.ata.tel.hud.low_beam";
            case 2 -> "gui.ata.tel.hud.high_beam";
            default -> "gui.ata.tel.hud.off";
        };
    }

    public static boolean toggled(boolean on, boolean works) {
        return !on && works;
    }

    public static boolean afterTick(boolean on, boolean works) {
        return on && works;
    }
}
