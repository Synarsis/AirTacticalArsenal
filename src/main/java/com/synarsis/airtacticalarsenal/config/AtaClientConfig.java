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
    public static final ModConfigSpec.DoubleValue TEL_CAMERA_DISTANCE;
    public static final ModConfigSpec.BooleanValue TEL_HUD;
    public static final ModConfigSpec.EnumValue<FxQuality> FX_QUALITY;
    public static final ModConfigSpec.BooleanValue FX_SCREEN_FLASH;
    public static final ModConfigSpec.BooleanValue FX_CAMERA_SHAKE;
    public static final ModConfigSpec.EnumValue<FxLighting> FX_LIGHTING;
    public static final ModConfigSpec.DoubleValue FX_LIGHT_INTENSITY;
    public static final ModConfigSpec.BooleanValue FX_HEAT_HAZE;
    public static final ModConfigSpec.BooleanValue FX_HEADLIGHT_BEAMS;
    public static final ModConfigSpec.BooleanValue FX_BLOOM;
    public static final ModConfigSpec.BooleanValue FX_GLARE;

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

        BUILDER.push("Iskander TEL");
        TEL_CAMERA_DISTANCE = BUILDER
                .comment("Third-person camera distance while riding the launcher, in blocks")
                .defineInRange("telCameraDistance", 16.0, 4.0, 40.0);
        TEL_HUD = BUILDER
                .comment("Show speed, hull and missile status while riding the launcher")
                .define("telHud", true);
        BUILDER.pop();

        BUILDER.push("Effects");
        BUILDER.comment("Smoke, fire and flashes of the launcher and the missile (launch, flight, impact).");
        FX_QUALITY = BUILDER
                .comment("How many particles the effects may use: LOW 35%, MEDIUM 65%, HIGH all of them")
                .defineEnum("fxQuality", FxQuality.HIGH);
        FX_SCREEN_FLASH = BUILDER
                .comment("Flash the screen white when a blast is in view")
                .define("fxScreenFlash", true);
        FX_CAMERA_SHAKE = BUILDER
                .comment("Shake the camera when a launch or a blast is near")
                .define("fxCameraShake", true);
        FX_LIGHTING = BUILDER
                .comment("The lighting of blasts, rocket flames and the launcher's headlights on the world.",
                        "OFF: none. LOW: 8 lights, no normals, no shadows. MEDIUM: 16 lights, normals.",
                        "HIGH: 32 lights, full resolution, contact shadows. Off by itself under an Iris/Oculus shader pack.")
                .defineEnum("fxLighting", FxLighting.HIGH);
        FX_LIGHT_INTENSITY = BUILDER
                .comment("Strength of all those lights, 1.0 as designed")
                .defineInRange("fxLightIntensity", 1.0, 0.0, 2.0);
        FX_HEAT_HAZE = BUILDER
                .comment("Hot air shakes the picture over flames and fireballs (only with fxLighting HIGH)")
                .define("fxHeatHaze", true);
        FX_HEADLIGHT_BEAMS = BUILDER.define("fxHeadlightBeams", true);
        FX_BLOOM = BUILDER.define("fxBloom", true);
        FX_GLARE = BUILDER.define("fxGlare", true);
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

    public static float getTelCameraDistance() {
        return TEL_CAMERA_DISTANCE.get().floatValue();
    }

    public static boolean isTelHudEnabled() {
        return TEL_HUD.get();
    }

    public static FxQuality getFxQuality() {
        return FX_QUALITY.get();
    }

    public static FxLighting getFxLighting() {
        return FX_LIGHTING.get();
    }

    public static float getFxLightIntensity() {
        return FX_LIGHT_INTENSITY.get().floatValue();
    }

    public static boolean isFxHeadlightBeamsEnabled() {
        return FX_HEADLIGHT_BEAMS.get();
    }

    public static boolean isFxBloomEnabled() {
        return FX_BLOOM.get();
    }

    public static boolean isFxGlareEnabled() {
        return FX_GLARE.get();
    }

    public static boolean isFxHeatHazeEnabled() {
        return FX_HEAT_HAZE.get();
    }

    public static boolean isFxScreenFlashEnabled() {
        return FX_SCREEN_FLASH.get();
    }

    public static boolean isFxCameraShakeEnabled() {
        return FX_CAMERA_SHAKE.get();
    }

    public static boolean isHitboxOverlayEnabled() {
        return HITBOX_OVERLAY.get();
    }

    public static void setHitboxOverlay(boolean value) {
        HITBOX_OVERLAY.set(value);
        HITBOX_OVERLAY.save();
    }
}
