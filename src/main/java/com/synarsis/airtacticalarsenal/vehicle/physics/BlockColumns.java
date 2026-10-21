package com.synarsis.airtacticalarsenal.vehicle.physics;

@FunctionalInterface
public interface BlockColumns {

    double solidTop(double x, double z, double lowY, double highY);
}
