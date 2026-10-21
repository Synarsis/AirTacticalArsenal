package com.synarsis.airtacticalarsenal.debug;

public final class DebugOverrides {

    private static boolean noLaunchLimits;

    private DebugOverrides() {
    }

    public static boolean noLaunchLimits() {
        return noLaunchLimits;
    }

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
