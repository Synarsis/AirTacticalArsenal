package com.synarsis.airtacticalarsenal.entity;

public final class CrewAccess {

    private CrewAccess() {
    }

    public static boolean mayOperate(boolean riding, int seat, int crewSeats) {
        return riding && seat >= 0 && seat < crewSeats;
    }
}
