package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;

public final class TelDriving {

    private TelDriving() {
    }

    public static JoltVehicle.Controls controls(int keys) {
        double throttle = ((keys & TelInput.FWD) != 0 ? 1.0 : 0.0) - ((keys & TelInput.BACK) != 0 ? 1.0 : 0.0);
        double toTheRight = ((keys & TelInput.RIGHT) != 0 ? 1.0 : 0.0) - ((keys & TelInput.LEFT) != 0 ? 1.0 : 0.0);
        double handbrake = (keys & TelInput.BRAKE) != 0 ? 1.0 : 0.0;
        return JoltVehicle.Controls.driving(throttle, toTheRight, 0.0, handbrake, (keys & TelInput.BOOST) != 0);
    }
}
