package com.synarsis.airtacticalarsenal.combat.outcome;

/** Condition of the warhead and its fuze. */
public enum FuzeState {

    ARMED("armed"),
    DEGRADED("degraded"),
    DEAD("dead");

    private final String id;

    FuzeState(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    private static final float ARMED_THRESHOLD = 0.5f;

    public static FuzeState fromWarhead(float remaining, float capacity) {
        if (capacity <= 0.0f) {
            return ARMED;
        }
        if (remaining <= 0.0f) {
            return DEAD;
        }
        return remaining / capacity >= ARMED_THRESHOLD ? ARMED : DEGRADED;
    }
}
