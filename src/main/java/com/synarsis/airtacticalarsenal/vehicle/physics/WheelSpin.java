package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class WheelSpin {

    private static final double FULL_TURN = 2.0 * Math.PI;

    private WheelSpin() {
    }

    public static double delta(double previous, double now) {
        double difference = (now - previous) % FULL_TURN;
        if (difference > Math.PI) {
            difference -= FULL_TURN;
        } else if (difference < -Math.PI) {
            difference += FULL_TURN;
        }
        return difference;
    }

    public static double boneRotation(double rolledTurn) {
        return -rolledTurn;
    }

    public static double travelRotation(double forwardMetres, double radius) {
        return boneRotation(forwardMetres / radius);
    }
}
