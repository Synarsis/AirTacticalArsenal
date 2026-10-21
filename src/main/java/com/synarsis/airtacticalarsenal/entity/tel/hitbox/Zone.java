package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;

public enum Zone implements HitTag {

    CAB("cab", 0x34C759, 1.25f),
    ENGINE("engine", 0xAF52DE, 1.5f),
    CHASSIS("chassis", 0xC7C7CC, 1.0f),
    BAY("bay", 0xFF9500, 1.0f),
    COVER("cover", 0xFFCC00, 0.6f),
    ERECTOR("erector", 0x5856D6, 1.0f),
    MISSILE_1("missile_1", 0xFF3B30, 1.0f),
    MISSILE_2("missile_2", 0xFF2D55, 1.0f),
    WHEEL("wheel", 0x30B0C7, 0.5f),
    JACK("jack", 0x8E8E93, 0.6f);

    private final String id;
    private final int debugColor;
    private final float damageMultiplier;

    Zone(String id, int debugColor, float damageMultiplier) {
        this.id = id;
        this.debugColor = debugColor;
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public int debugColor() {
        return this.debugColor;
    }

    public float damageMultiplier() {
        return this.damageMultiplier;
    }

    public boolean isMissile() {
        return this == MISSILE_1 || this == MISSILE_2;
    }

    public static Zone ofBone(String bone) {
        if (bone.equals("cab") || bone.startsWith("door_")) {
            return CAB;
        }
        if (bone.equals("engine_deck")) {
            return ENGINE;
        }
        if (bone.equals("chassis") || bone.equals("vehicle_root")) {
            return CHASSIS;
        }
        if (bone.equals("bay") || bone.equals("cradle_fixed_mounts")) {
            return BAY;
        }
        if (bone.startsWith("cover_")) {
            return COVER;
        }
        if (bone.startsWith("erector_")) {
            return ERECTOR;
        }
        if (bone.startsWith("round_left_")) {
            return MISSILE_1;
        }
        if (bone.startsWith("round_right_")) {
            return MISSILE_2;
        }
        if (bone.startsWith("wheel_")) {
            return WHEEL;
        }
        if (bone.startsWith("jack_")) {
            return JACK;
        }
        return null;
    }
}
