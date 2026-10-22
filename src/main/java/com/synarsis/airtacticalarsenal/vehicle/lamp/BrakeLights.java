package com.synarsis.airtacticalarsenal.vehicle.lamp;

public final class BrakeLights {

    public static final double MOVING_MS = 0.8;
    public static final double DECELERATION_PER_TICK = 0.06;
    public static final int HOLD_TICKS = 6;
    public static final double DIM = 0.5;

    private double last;
    private boolean known;
    private int hold;

    public void update(double forwardMetresPerSecond, boolean driven) {
        this.update(forwardMetresPerSecond, driven, false, false, false);
    }

    public void update(double forwardMetresPerSecond, boolean driven, boolean localDriver,
            boolean forwardPressed, boolean backwardPressed) {
        double speed = Math.abs(forwardMetresPerSecond);
        if (!driven) {
            this.hold = 0;
        } else if (localDriver) {
            boolean braking = forwardMetresPerSecond > MOVING_MS && backwardPressed
                    || forwardMetresPerSecond < -MOVING_MS && forwardPressed;
            this.hold = braking ? HOLD_TICKS : 0;
        } else if (this.known) {
            double before = Math.abs(this.last);
            boolean sameWay = this.last * forwardMetresPerSecond >= 0.0;
            boolean slowing = before > MOVING_MS && (!sameWay || before - speed > DECELERATION_PER_TICK);
            if (slowing) {
                this.hold = HOLD_TICKS;
            } else if (this.hold > 0) {
                this.hold--;
            }
        }
        this.last = forwardMetresPerSecond;
        this.known = true;
    }

    public boolean braking() {
        return this.hold > 0;
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
