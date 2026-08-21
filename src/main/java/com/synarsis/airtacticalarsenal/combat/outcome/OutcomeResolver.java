package com.synarsis.airtacticalarsenal.combat.outcome;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;

/** Decides what knocking out a section does to the airframe. */
public final class OutcomeResolver {

    private OutcomeResolver() {
    }

    /**
     * The mode a knocked-out section puts the drone into.
     *
     * @param part the section that just reached zero
     * @param current the mode it is already in, so a second hit escalates rather than resets
     * @return the new mode, which may be {@code current} if this section changes nothing
     */
    public static FallMode onKnockedOut(HitPart part, FallMode current) {
        FallMode next = switch (part) {
            // Losing a wing beats anything else: nothing flies on one panel.
            case WING_LEFT, WING_RIGHT -> FallMode.SPIRAL;
            // No pitch or yaw authority left.
            case TAIL -> FallMode.TUMBLE;
            // Thrust gone. Still a glider -- unless the airframe is already hurt, and then it is not.
            case ENGINE, PROP -> current == FallMode.NONE ? FallMode.GLIDE : FallMode.DEAD_STICK;
            // Fire in the tank. It keeps flying for now; the fire is what ends it.
            case FUEL -> current == FallMode.NONE ? FallMode.BURNING : current;
            // Guidance. The most interesting outcome: it arrives, just not where it was sent.
            case AVIONICS -> current == FallMode.NONE ? FallMode.VEER : current;
            // Structure and the charge itself change nothing on their own.
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

    /** Keeps the worse of two modes, so a later, lesser hit cannot rescue a drone. */
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
