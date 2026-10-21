package com.synarsis.airtacticalarsenal.client.fx;

public final class LaunchTrigger {

    public static final int ENDING_TICKS = 3;
    public static final int QUIET_TICKS = 100;

    private int lastLeft = -1;
    private long firedAt = -1000;

    public boolean observe(int left, boolean aboard, long now) {
        boolean launched = left < 0 && this.lastLeft >= 0 && this.lastLeft <= ENDING_TICKS && !aboard;
        this.lastLeft = left;
        return launched && this.claim(now);
    }

    public boolean missileSeen(long now) {
        return this.claim(now);
    }

    public boolean recent(long now) {
        return now - this.firedAt < QUIET_TICKS;
    }

    private boolean claim(long now) {
        if (this.recent(now)) {
            return false;
        }
        this.firedAt = now;
        return true;
    }
}
