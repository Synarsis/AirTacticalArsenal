package com.synarsis.airtacticalarsenal.entity.tel.physics;

public final class TelSway {

    public static final double OMEGA = 4.0 * Math.PI;
    public static final double ZETA = 0.6;
    public static final double DT = 0.05;
    public static final double MAX_ACCELERATION = 0.02;
    public static final double PITCH_PER_ACCELERATION = 15.0;
    public static final double ROLL_PER_ACCELERATION = 20.0;
    public static final double LEAK = 0.8;

    private double pitch;
    private double roll;
    private double pitchVel;
    private double rollVel;

    public double fakePitch;
    public double fakeRoll;

    public void tick(double forward, double right, double rate) {
        double len = Math.hypot(forward, right);
        if (len > MAX_ACCELERATION) {
            double k = MAX_ACCELERATION / len;
            forward *= k;
            right *= k;
        }
        double pitchTarget = PITCH_PER_ACCELERATION * forward;
        double rollTarget = ROLL_PER_ACCELERATION * right;

        double pitchAcc = OMEGA * OMEGA * (pitchTarget - this.pitch) - 2.0 * ZETA * OMEGA * this.pitchVel;
        this.pitchVel += pitchAcc * DT;
        this.pitch += this.pitchVel * DT;

        double rollAcc = OMEGA * OMEGA * (rollTarget - this.roll) - 2.0 * ZETA * OMEGA * this.rollVel;
        this.rollVel += rollAcc * DT;
        this.roll += this.rollVel * DT;

        this.fakePitch = this.fakePitch * LEAK - this.pitch * rate;
        this.fakeRoll = this.fakeRoll * LEAK - this.roll * rate;
    }
}
