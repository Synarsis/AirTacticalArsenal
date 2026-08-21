package com.synarsis.airtacticalarsenal.combat.hitbox;

/** A structural section of an airframe that can be hit and knocked out on its own. */
public enum HitPart {

    WARHEAD("warhead", 0xFF3B30),
    AVIONICS("avionics", 0xFFCC00),
    FUEL("fuel", 0xFF9500),
    BODY("body", 0xC7C7CC),
    WING_LEFT("wing_left", 0x34C759),
    WING_RIGHT("wing_right", 0x30B0C7),
    ENGINE("engine", 0xAF52DE),
    PROP("prop", 0x5856D6),
    TAIL("tail", 0x007AFF);

    private final String id;
    private final int debugColor;

    HitPart(String id, int debugColor) {
        this.id = id;
        this.debugColor = debugColor;
    }

    public String id() {
        return this.id;
    }

    public int debugColor() {
        return this.debugColor;
    }
}
