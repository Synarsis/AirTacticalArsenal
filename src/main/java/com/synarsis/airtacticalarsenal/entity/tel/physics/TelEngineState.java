package com.synarsis.airtacticalarsenal.entity.tel.physics;

public final class TelEngineState {

    public double power;
    public double deltaRot;
    public int holdTick;
    public double rudder;
    public double wheelRotL;
    public double wheelRotR;

    public void reset() {
        this.power = 0.0;
        this.deltaRot = 0.0;
        this.holdTick = 0;
        this.rudder = 0.0;
    }

    public double steerDegrees() {
        return Math.max(-25.0, Math.min(25.0, -this.rudder * 40.0));
    }
}
