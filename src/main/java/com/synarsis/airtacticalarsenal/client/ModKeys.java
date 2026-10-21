package com.synarsis.airtacticalarsenal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class ModKeys {

    private static final String CATEGORY = "key.categories.ata";

    public static final KeyMapping SETTINGS = new KeyMapping(
            "key.ata.settings", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static final KeyMapping CAMERA_EXIT = new KeyMapping(
            "key.ata.camera_exit", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    public static final KeyMapping CAMERA_LOCK = new KeyMapping(
            "key.ata.camera_lock", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public static final KeyMapping NIGHT_VISION = new KeyMapping(
            "key.ata.night_vision", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    public static final KeyMapping TEL_FIRE_CONTROL = new KeyMapping(
            "key.ata.tel_fire_control", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static final KeyMapping TEL_HEADLIGHTS = new KeyMapping(
            "key.ata.tel_headlights", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private ModKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SETTINGS);
        event.register(CAMERA_EXIT);
        event.register(CAMERA_LOCK);
        event.register(NIGHT_VISION);
        event.register(TEL_FIRE_CONTROL);
        event.register(TEL_HEADLIGHTS);
    }
}
