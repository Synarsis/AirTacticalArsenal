package com.synarsis.airtacticalarsenal.config;

public enum FxQuality {
    LOW(0.35),
    MEDIUM(0.65),
    HIGH(1.0);

    private final double multiplier;

    FxQuality(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return this.multiplier;
    }
}
