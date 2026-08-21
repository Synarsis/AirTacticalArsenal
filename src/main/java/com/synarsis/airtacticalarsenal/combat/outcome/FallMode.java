package com.synarsis.airtacticalarsenal.combat.outcome;

/** How a crippled airframe comes down. */
public enum FallMode {

    NONE("none"),
    GLIDE("glide"),
    DEAD_STICK("dead_stick"),
    SPIRAL("spiral"),
    TUMBLE("tumble"),
    VEER("veer"),
    BURNING("burning");

    private final String id;

    FallMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean isFalling() {
        return this != NONE && this != VEER && this != BURNING;
    }

    public boolean isPowered() {
        return this == NONE || this == VEER || this == BURNING;
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    public static FallMode fromByte(byte value) {
        FallMode[] all = values();
        return value >= 0 && value < all.length ? all[value] : NONE;
    }
}
