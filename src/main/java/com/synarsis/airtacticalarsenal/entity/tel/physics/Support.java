package com.synarsis.airtacticalarsenal.entity.tel.physics;

public enum Support {
    HANGING,
    CONTACT,
    WALL;

    public static Support classify(double top, double bottom, double climb) {
        if (Double.isNaN(top)) {
            return HANGING;
        }
        return top > Suspension.windowTop(bottom, climb) ? WALL : CONTACT;
    }
}
