package com.synarsis.airtacticalarsenal.combat.outcome;

/** What a crippled airframe does when it reaches the ground. */
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

    /**
     * Decides the outcome. Pure, so the whole table can be walked in a test.
     *
     * @param fuze condition of the warhead
     * @param impactSpeed how fast it arrived, blocks per tick
     * @param armedMayFire whether an intact charge is allowed to go off after being shot down at
     * all -- the config switch that lets a server say a downed drone is a
     * downed drone
     * @param dudChance chance a degraded charge simply fails, 0..1
     * @param roll a value in {@code [0, 1)}; passed in rather than drawn here so the
     * decision can be exercised without a random source
     */
    public static ImpactOutcome resolve(FuzeState fuze, double impactSpeed, boolean armedMayFire,
                                        double dudChance, double roll) {
        if (fuze == FuzeState.DEAD) {
            return DUD;
        }
        if (fuze == FuzeState.ARMED) {
            return armedMayFire ? FULL : LOW_ORDER;
        }

        // Degraded: a hard arrival can still set it off, a gentle one usually does not.
        if (!armedMayFire) {
            return roll < dudChance ? DUD : LOW_ORDER;
        }
        if (impactSpeed >= HARD_ARRIVAL_SPEED) {
            return roll < dudChance * 0.5 ? DUD : FULL;
        }
        return roll < dudChance ? DUD : LOW_ORDER;
    }
}
