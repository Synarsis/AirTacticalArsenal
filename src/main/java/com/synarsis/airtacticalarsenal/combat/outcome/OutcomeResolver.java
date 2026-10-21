package com.synarsis.airtacticalarsenal.combat.outcome;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;

public final class OutcomeResolver {

    private OutcomeResolver() {
    }

    public static FallMode onKnockedOut(HitPart part, FallMode current) {
        FallMode next = switch (part) {
            case WING_LEFT, WING_RIGHT -> FallMode.SPIRAL;
            case TAIL -> FallMode.TUMBLE;
            case ENGINE, PROP -> current == FallMode.NONE ? FallMode.GLIDE : FallMode.DEAD_STICK;
            case FUEL -> current == FallMode.NONE ? FallMode.BURNING : current;
            case AVIONICS -> current == FallMode.NONE ? FallMode.VEER : current;
            case BODY, WARHEAD -> current;
        };
        return escalate(current, next);
    }

    public static FallMode onDurabilityExhausted(FallMode current) {
        return current.isFalling() ? current : FallMode.TUMBLE;
    }

    public static FallMode onFireSpread(FallMode current) {
        return current == FallMode.BURNING ? FallMode.SPIRAL : current;
    }

    private static FallMode escalate(FallMode current, FallMode next) {
        return severity(next) >= severity(current) ? next : current;
    }

    private static int severity(FallMode mode) {
        return switch (mode) {
            case NONE -> 0;
            case VEER -> 1;
            case BURNING -> 2;
            case GLIDE -> 3;
            case DEAD_STICK -> 4;
            case SPIRAL -> 5;
            case TUMBLE -> 6;
        };
    }
}
