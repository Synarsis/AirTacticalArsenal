package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelDriving;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;

public final class TractorDrive {

    private TractorDrive() {
    }

    public static JoltVehicle.Controls controlsFor(int keys) {
        return TelDriving.controls(keys);
    }

    public static boolean overturned(double pitchDeg, double rollDeg) {
        return Math.abs(pitchDeg) > 60.0 || Math.abs(rollDeg) > 60.0;
    }
}
