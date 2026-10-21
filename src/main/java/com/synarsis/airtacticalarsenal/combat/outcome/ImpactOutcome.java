package com.synarsis.airtacticalarsenal.combat.outcome;

public enum ImpactOutcome {

    FULL("full"),
    LOW_ORDER("low_order"),
    DUD("dud");

    private final String id;

    ImpactOutcome(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    private static final double HARD_ARRIVAL_SPEED = 1.2;

    public static ImpactOutcome resolve(FuzeState fuze, double impactSpeed, boolean armedMayFire,
                                        double dudChance, double roll) {
        if (fuze == FuzeState.DEAD) {
            return DUD;
        }
        if (fuze == FuzeState.ARMED) {
            return armedMayFire ? FULL : LOW_ORDER;
        }

        if (!armedMayFire) {
            return roll < dudChance ? DUD : LOW_ORDER;
        }
        if (impactSpeed >= HARD_ARRIVAL_SPEED) {
            return roll < dudChance * 0.5 ? DUD : FULL;
        }
        return roll < dudChance ? DUD : LOW_ORDER;
    }
}
