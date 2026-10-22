package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import java.util.function.Consumer;

final class BloomPass {
    private static final LightPostTarget[] DOWN = new LightPostTarget[5];
    private static final LightPostTarget[] UP = new LightPostTarget[4];
    private static int texture;
    static {
        for (int index = 0; index < DOWN.length; index++) DOWN[index] = new LightPostTarget();
        for (int index = 0; index < UP.length; index++) UP[index] = new LightPostTarget();
    }
    private BloomPass() {
    }
    static void draw(int width, int height, Consumer<ShaderInstance> setup) {
        texture = 0;
        RenderSystem.disableBlend();
        if (!DOWN[0].bind(Math.max(1, width / 2), Math.max(1, height / 2))) return;
        ShaderInstance excess = LightShaders.excess();
        RenderSystem.setShader(() -> excess);
        setup.accept(excess);
        LightPass.quad(-1, -1, 1, 1);
        excess.clear();
        for (int index = 1; index < 5; index++) {
            if (!DOWN[index].bind(Math.max(1, width / BloomMath.divisor(index)),
                    Math.max(1, height / BloomMath.divisor(index)))) return;
            filter(DOWN[index - 1], false);
        }
        LightPostTarget source = DOWN[4];
        for (int index = 3; index >= 0; index--) {
            if (!UP[index].bind(DOWN[index].width, DOWN[index].height)) return;
            filter(source, true);
            source = UP[index];
        }
        texture = source.texture;
    }
    private static void filter(LightPostTarget source, boolean up) {
        ShaderInstance shader = LightShaders.filter();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("SourceSampler", source.texture);
        shader.safeGetUniform("PixelSize").set(1.0f / source.width, 1.0f / source.height);
        shader.safeGetUniform("Upsample").set(up ? 1.0f : 0.0f);
        LightPass.quad(-1, -1, 1, 1);
        shader.clear();
    }
    static int texture() {return texture;}
    static void reset() {texture = 0;}
    static void release() {
        reset();
        for (LightPostTarget target : DOWN) target.release();
        for (LightPostTarget target : UP) target.release();
    }
}
