package com.synarsis.airtacticalarsenal.vehicle.physics;

public final class PhysicsLayers {

    public static final int MAX_VEHICLES = 16;

    public static final int BROAD_NON_MOVING = 0;
    public static final int BROAD_MOVING = 1;
    public static final int BROAD_COUNT = 2;

    public static final int DEBRIS = 0;
    private static final int FIRST_VEHICLE = 1;
    private static final int FIRST_GROUND = FIRST_VEHICLE + MAX_VEHICLES;
    private static final int FIRST_WHEEL = FIRST_GROUND + MAX_VEHICLES;
    public static final int OBJECT_COUNT = FIRST_WHEEL + MAX_VEHICLES;

    private PhysicsLayers() {
    }

    public static int vehicle(int slot) {
        return FIRST_VEHICLE + slot;
    }

    public static int ground(int slot) {
        return FIRST_GROUND + slot;
    }

    public static int wheel(int slot) {
        return FIRST_WHEEL + slot;
    }

    public static boolean isWheel(int layer) {
        return layer >= FIRST_WHEEL && layer < OBJECT_COUNT;
    }

    public static boolean isVehicle(int layer) {
        return layer >= FIRST_VEHICLE && layer < FIRST_GROUND;
    }

    public static boolean isGround(int layer) {
        return layer >= FIRST_GROUND && layer < OBJECT_COUNT;
    }

    public static int broadPhase(int layer) {
        return isGround(layer) ? BROAD_NON_MOVING : BROAD_MOVING;
    }

    public static boolean collide(int a, int b) {
        if (isWheel(a) || isWheel(b)) {
            if (isWheel(a) && isGround(b)) {
                return a - FIRST_WHEEL == b - FIRST_GROUND;
            }
            return isWheel(b) && isGround(a) && b - FIRST_WHEEL == a - FIRST_GROUND;
        }
        if (isVehicle(a) && isVehicle(b)) {
            return a != b;
        }
        if (isVehicle(a) && isGround(b)) {
            return a - FIRST_VEHICLE == b - FIRST_GROUND;
        }
        if (isGround(a) && isVehicle(b)) {
            return collide(b, a);
        }
        if (a == DEBRIS && isGround(b) || b == DEBRIS && isGround(a)) {
            return true;
        }
        if (a == DEBRIS && isVehicle(b) || b == DEBRIS && isVehicle(a)) {
            return true;
        }
        return a == DEBRIS && b == DEBRIS;
    }
}
