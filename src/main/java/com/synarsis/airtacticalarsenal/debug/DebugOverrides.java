package com.synarsis.airtacticalarsenal.debug;

/** Testing switches that lift the mod's launch restrictions. */
public final class DebugOverrides {

    private static boolean noLaunchLimits;

    private DebugOverrides() {
    }

    /**
     * When on: no minimum or maximum launch distance, no blacklist or whitelist zones, and no
     * firing-arc limit on launchers.
     */
    public static boolean noLaunchLimits() {
        return noLaunchLimits;
    }

    /**
     * Inverse of {@link #noLaunchLimits()}, for the many checks shaped {@code if (limitsApply() &&
     * somethingIsOutOfRange)}.
     */
    public static boolean limitsApply() {
        return !noLaunchLimits;
    }

    public static void setNoLaunchLimits(boolean value) {
        noLaunchLimits = value;
    }

    public static void reset() {
        noLaunchLimits = false;
    }
}
