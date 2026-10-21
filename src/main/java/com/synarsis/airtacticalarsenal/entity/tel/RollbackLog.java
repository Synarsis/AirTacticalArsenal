package com.synarsis.airtacticalarsenal.entity.tel;

import javax.annotation.Nullable;

final class RollbackLog {

    static final int MIN_GAP_TICKS = 100;

    private int lastReportTick = Integer.MIN_VALUE;
    private int pending;

    @Nullable
    String note(int tick, int vehicleId, String driver, String reason) {
        this.pending++;
        if (this.lastReportTick != Integer.MIN_VALUE && tick - this.lastReportTick < MIN_GAP_TICKS) {
            return null;
        }
        int count = this.pending;
        this.pending = 0;
        this.lastReportTick = tick;
        return String.format(java.util.Locale.ROOT, "Launcher %d of %s was rolled back: %s (%d rollback%s since the last report)",
                vehicleId, driver, reason, count, count == 1 ? "" : "s");
    }
}
