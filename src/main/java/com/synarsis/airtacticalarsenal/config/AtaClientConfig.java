package com.synarsis.airtacticalarsenal.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AtaClientConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HIT_MARKER_ENABLED;
    public static final ModConfigSpec.BooleanValue HIT_MARKER_SOUND;
    public static final ModConfigSpec.DoubleValue HIT_MARKER_SCALE;
    public static final ModConfigSpec.IntValue HIT_MARKER_TICKS;
    public static final ModConfigSpec.BooleanValue PART_LABELS;
    public static final ModConfigSpec.BooleanValue HITBOX_OVERLAY;

    private AtaClientConfig() {
    }

    static {
        BUILDER.push("Hit Feedback");
        BUILDER.comment("What you get told when you hit something in the air.");
        HIT_MARKER_ENABLED = BUILDER
                .comment("Show a marker on the crosshair when a shot lands on a drone")
                .define("hitMarkerEnabled", true);
        HIT_MARKER_SOUND = BUILDER
                .comment("Play a short note on a hit, and a duller one when a shot fails to penetrate")
                .define("hitMarkerSound", true);
        HIT_MARKER_SCALE = BUILDER
                .comment("Size of the marker")
                .defineInRange("hitMarkerScale", 1.0, 0.5, 2.5);
        HIT_MARKER_TICKS = BUILDER
                .comment("How long the marker stays up, in ticks")
                .defineInRange("hitMarkerTicks", 8, 2, 40);
        PART_LABELS = BUILDER
                .comment("Name the section that was hit under the crosshair -- wing, engine, warhead")
                .define("partHitLabels", true);
        BUILDER.pop();

        BUILDER.push("Debug");
        HITBOX_OVERLAY = BUILDER
                .comment("Draw the hit geometry as a wireframe.",
                        "The /ata debug hitbox command sets this for you; it is here so it survives a restart.")
                .define("hitboxOverlay", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean isHitMarkerEnabled() {
        return HIT_MARKER_ENABLED.get();
    }

    public static boolean isHitMarkerSoundEnabled() {
        return HIT_MARKER_SOUND.get();
    }

    public static float getHitMarkerScale() {
        return HIT_MARKER_SCALE.get().floatValue();
    }

    public static int getHitMarkerTicks() {
        return HIT_MARKER_TICKS.get();
    }

    public static boolean showPartLabels() {
        return PART_LABELS.get();
    }

    public static boolean isHitboxOverlayEnabled() {
        return HITBOX_OVERLAY.get();
    }

    public static void setHitboxOverlay(boolean value) {
        HITBOX_OVERLAY.set(value);
        HITBOX_OVERLAY.save();
    }
}
