package com.synarsis.airtacticalarsenal.client.fx.light;

public final class LightTimings {

    public static final int LEVELS = 0;
    public static final int SOLIDITY = 1;
    public static final int BEAM_CLIPS = 2;
    private static final int KINDS = 3;
    private static final long WINDOW_NANOS = 1_000_000_000L;

    private static final LightTimings SHARED = new LightTimings();

    private final long[] frame = new long[KINDS];
    private final long[] window = new long[KINDS];
    private final double[] average = new double[KINDS];
    private int frames;
    private long windowStart = Long.MIN_VALUE;

    public static LightTimings shared() {
        return SHARED;
    }

    public synchronized void add(int kind, long nanos) {
        this.frame[kind] += nanos;
    }

    public synchronized void endFrame(long nowNanos) {
        if (this.windowStart == Long.MIN_VALUE) {
            this.windowStart = nowNanos;
        }
        for (int i = 0; i < KINDS; i++) {
            this.window[i] += this.frame[i];
            this.frame[i] = 0L;
        }
        this.frames++;
        if (nowNanos - this.windowStart >= WINDOW_NANOS) {
            for (int i = 0; i < KINDS; i++) {
                this.average[i] = this.window[i] / 1.0E6 / Math.max(1, this.frames);
                this.window[i] = 0L;
            }
            this.frames = 0;
            this.windowStart = nowNanos;
        }
    }

    public synchronized double millis(int kind) {
        return this.average[kind];
    }

    public synchronized String line() {
        return String.format(java.util.Locale.ROOT, "levels %.2f ms, solidity %.2f ms, beam clips %.2f ms",
                this.average[LEVELS], this.average[SOLIDITY], this.average[BEAM_CLIPS]);
    }

    public static void time(int kind, Runnable work) {
        long started = System.nanoTime();
        try {
            work.run();
        } finally {
            SHARED.add(kind, System.nanoTime() - started);
        }
    }
}
