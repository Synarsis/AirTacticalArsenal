package com.synarsis.airtacticalarsenal.entity.tel;

public final class TelHeadlights {

    private TelHeadlights() {
    }

    public static boolean works(float health, float maxHealth, boolean destroyed, float burningFraction) {
        return !destroyed && health > 0.0f && health > maxHealth * burningFraction;
    }

    public static boolean toggled(boolean on, boolean works) {
        return !on && works;
    }

    public static boolean afterTick(boolean on, boolean works) {
        return on && works;
    }
}
