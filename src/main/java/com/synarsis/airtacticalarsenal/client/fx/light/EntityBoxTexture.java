package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.synarsis.airtacticalarsenal.client.fx.light.EntityShadowMath.Box;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.List;

@OnlyIn(Dist.CLIENT)
final class EntityBoxTexture {

    static final int WIDTH = EntityShadowMath.MAX_BOXES;
    static final int ROWS = 11;

    private static int texture;
    private static FloatBuffer staging;
    private static int count;

    private EntityBoxTexture() {
    }

    static float[] pack(List<Box> boxes) {
        float[] out = new float[WIDTH * ROWS * 4];
        int n = Math.min(boxes.size(), WIDTH);
        for (int i = 0; i < n; i++) {
            Box b = boxes.get(i);
            int first = i * 4;
            int second = (WIDTH + i) * 4;
            int third = (2 * WIDTH + i) * 4;
            out[first] = (float) b.cx();
            out[first + 1] = (float) b.cy();
            out[first + 2] = (float) b.cz();
            out[first + 3] = b.owner();
            out[second] = (float) b.hx();
            out[second + 1] = (float) b.hy();
            out[second + 2] = (float) b.hz();
            out[third] = (float) b.axisX();
            out[third + 1] = (float) b.axisZ();
        }
        return out;
    }

    static int upload(List<Box> boxes) {
        count = Math.min(boxes.size(), WIDTH);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GlStateManager._bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            TexUpload.Store store = TexUpload.begin();
            try {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, WIDTH, ROWS, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                        (java.nio.ByteBuffer) null);
            } finally {
                TexUpload.end(store);
            }
        } else {
            GlStateManager._bindTexture(texture);
        }
        if (staging == null) {
            staging = BufferUtils.createFloatBuffer(WIDTH * ROWS * 4);
        }
        staging.clear();
        staging.put(pack(boxes));
        staging.flip();
        TexUpload.Store store = TexUpload.begin();
        try {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, ROWS, GL11.GL_RGBA, GL11.GL_FLOAT, staging);
        } finally {
            TexUpload.end(store);
        }
        GlStateManager._bindTexture(previous);
        return count;
    }

    static void uploadRanges(float[] ranges) {
        if (texture == 0) upload(List.of());
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(WIDTH * 8 * 4);
        buffer.put(ranges).flip();
        TexUpload.Store store = TexUpload.begin();
        try {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 3, WIDTH, 8, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
        } finally {
            TexUpload.end(store);
            GlStateManager._bindTexture(previous);
        }
    }

    static int texture() {
        return texture;
    }

    static int count() {
        return count;
    }

    static void release() {
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
        count = 0;
    }
}
