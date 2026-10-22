package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL30;

final class LightPostTarget {
    int framebuffer;
    int texture;
    int width;
    int height;

    boolean bind(int width, int height) {
        if (framebuffer == 0 || this.width != width || this.height != height) {
            release();
            int[] made = LightPass.createColorTarget(width, height, true);
            if (made == null) return false;
            framebuffer = made[0];
            texture = made[1];
            this.width = width;
            this.height = height;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        RenderSystem.viewport(0, 0, width, height);
        return true;
    }

    void release() {
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (texture != 0) GlStateManager._deleteTexture(texture);
        framebuffer = 0;
        texture = 0;
    }
}
