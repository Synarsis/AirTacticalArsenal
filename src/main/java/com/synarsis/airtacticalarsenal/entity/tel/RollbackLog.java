package com.synarsis.airtacticalarsenal.entity.tel;

import javax.annotation.Nullable;

public final class RollbackLog {

    public static final int MIN_GAP_TICKS = 100;

    private int lastReportTick = Integer.MIN_VALUE;
    private int pending;

    @Nullable
    String note(int tick, int vehicleId, String driver, String reason) {
        return this.note("Launcher", tick, vehicleId, driver, reason);
    }

    @Nullable
    public String note(String label, int tick, int vehicleId, String driver, String reason) {
        this.pending++;
        if (this.lastReportTick != Integer.MIN_VALUE && tick - this.lastReportTick < MIN_GAP_TICKS) {
            return null;
        }
        int count = this.pending;
        this.pending = 0;
        this.lastReportTick = tick;
        return String.format(java.util.Locale.ROOT, "%s %d of %s was rolled back: %s (%d rollback%s since the last report)",
                label, vehicleId, driver, reason, count, count == 1 ? "" : "s");
    }
}
