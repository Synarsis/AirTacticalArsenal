package com.synarsis.airtacticalarsenal.vehicle.lamp;

public final class BrakeLights {

    public static final double MOVING_MS = 0.8;
    public static final double DECELERATION_PER_TICK = 0.06;
    public static final double PARKED_MS = 0.2;
    public static final int HOLD_TICKS = 6;
    public static final double DIM = 0.5;

    private double last;
    private boolean known;
    private int hold;
    private boolean parked;

    public void update(double forwardMetresPerSecond, boolean driven) {
        double speed = Math.abs(forwardMetresPerSecond);
        if (this.known) {
            double before = Math.abs(this.last);
            boolean sameWay = this.last * forwardMetresPerSecond >= 0.0;
            boolean slowing = before > MOVING_MS && (!sameWay || before - speed > DECELERATION_PER_TICK);
            if (slowing) {
                this.hold = HOLD_TICKS;
            } else if (this.hold > 0) {
                this.hold--;
            }
        }
        this.parked = !driven && speed < PARKED_MS;
        this.last = forwardMetresPerSecond;
        this.known = true;
    }

    public boolean braking() {
        return this.hold > 0 || this.parked;
    }

    public double rearBrightness(boolean lightsOn) {
        if (this.braking()) {
            return 1.0;
        }
        return lightsOn ? DIM : 0.0;
    }

    public static double frontBrightness(boolean lightsOn) {
        return lightsOn ? 1.0 : 0.0;
    }
}
