package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;

public enum PatriotZone implements HitTag {

    TRACTOR("tractor", 0x34C759, 1.0f),
    TRAILER("trailer", 0xC7C7CC, 1.0f),
    PACK("pack", 0xFF9500, 1.5f),
    ANTENNA("antenna", 0xFF9500, 1.5f),
    WHEEL("wheel", 0x30B0C7, 0.5f),
    SUPPORT("support", 0x8E8E93, 0.6f);

    private final String id;
    private final int debugColor;
    private final float damageMultiplier;

    PatriotZone(String id, int debugColor, float damageMultiplier) {
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

    public static PatriotZone ofBone(String bone) {
        if (bone.startsWith("truck_wheel_") || bone.startsWith("truck_steer_")) {
            return WHEEL;
        }
        if (bone.startsWith("truck_") || bone.equals("tractor_carrier")) {
            return TRACTOR;
        }
        if (bone.startsWith("wheel_")) {
            return WHEEL;
        }
        if (bone.startsWith("outrigger_") || bone.startsWith("jack_")) {
            return SUPPORT;
        }
        if (bone.equals("traverse") || bone.equals("elevation") || bone.startsWith("lift_")) {
            return PACK;
        }
        if (bone.equals("antenna") || bone.startsWith("radar_")) {
            return ANTENNA;
        }
        if (bone.equals("chassis") || bone.equals("root")) {
            return TRAILER;
        }
        return null;
    }
}
