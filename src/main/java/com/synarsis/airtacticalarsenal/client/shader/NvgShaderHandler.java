package com.synarsis.airtacticalarsenal.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class NvgShaderHandler {

    private static final ResourceLocation NVG_SHADER =
            new ResourceLocation("ata", "shaders/post/night-vision.json");

    private static boolean nvgEnabled = false;

    private static String cachedPassesFieldName = null;

    private static final String SHADER_NAME_MARKER = "ata";

    public static void enable() {
        nvgEnabled = true;
    }

    public static void disable() {
        nvgEnabled = false;
        try {
            GameRenderer renderer = Minecraft.getInstance().gameRenderer;
            PostChain current = renderer.currentEffect();
            if (current != null && isOurShader(current)) {
                renderer.shutdownEffect();
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isEnabled() {
        return nvgEnabled;
    }

    private static boolean isOurShader(PostChain effect) {
        String name = effect.getName();
        return name != null && name.contains(SHADER_NAME_MARKER);
    }

    public static void processShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        GameRenderer renderer = mc.gameRenderer;

        if (nvgEnabled) {
            PostChain current = renderer.currentEffect();

            if (current == null || !isOurShader(current)) {

                try {
                    renderer.loadEffect(NVG_SHADER);

                    PostChain effect = renderer.currentEffect();
                    if (effect != null) {
                        configureUniforms(effect);
                    }

                    RenderTarget mainTarget = mc.getMainRenderTarget();
                    mainTarget.bindWrite(true);
                    RenderSystem.bindTextureForSetup(mainTarget.getColorTextureId());
                    RenderSystem.bindTextureForSetup(mainTarget.getDepthTextureId());
                } catch (Throwable ignored) {}
            }
        } else {

            PostChain current = renderer.currentEffect();
            if (current != null && isOurShader(current)) {
                try {
                    renderer.shutdownEffect();
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void configureUniforms(PostChain effect) {
        List<PostPass> passes = getPasses(effect);
        for (PostPass pass : passes) {
            if (pass.getEffect().getUniform("NightVisionEnabled") != null) {
                pass.getEffect().safeGetUniform("NightVisionEnabled").set(1.0f);
                pass.getEffect().safeGetUniform("Brightness").set(0.6f);
                pass.getEffect().safeGetUniform("RedValue").set(0.2f);
                pass.getEffect().safeGetUniform("GreenValue").set(1.0f);
                pass.getEffect().safeGetUniform("BlueValue").set(0.2f);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static List<PostPass> getPasses(PostChain chain) {
        try {

            if (cachedPassesFieldName != null) {
                try {
                    Field f = PostChain.class.getDeclaredField(cachedPassesFieldName);
                    f.setAccessible(true);
                    Object v = f.get(chain);
                    if (v instanceof List<?>) return (List<PostPass>) v;
                    cachedPassesFieldName = null;
                } catch (Throwable ignored) {
                    cachedPassesFieldName = null;
                }
            }

            String[] names = {"passes", "f_110007_", "m_110007_"};
            for (String name : names) {
                try {
                    Field f = PostChain.class.getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(chain);
                    if (v instanceof List<?>) {
                        cachedPassesFieldName = name;
                        return (List<PostPass>) v;
                    }
                } catch (Throwable ignored) {}
            }

            for (Field f : PostChain.class.getDeclaredFields()) {
                try {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object v = f.get(chain);
                        if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof PostPass) {
                            cachedPassesFieldName = f.getName();
                            return (List<PostPass>) v;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return List.of();
    }
}
