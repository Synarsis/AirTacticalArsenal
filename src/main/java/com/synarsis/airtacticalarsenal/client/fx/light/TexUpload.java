package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;

import java.nio.ByteBuffer;

final class TexUpload {

    record Params(int rowLength, int skipPixels, int skipRows, int alignment) {
    }

    record Store(int rowLength, int skipPixels, int skipRows, int alignment, int unpackBuffer) {
    }

    private TexUpload() {
    }

    static Params params() {
        return new Params(0, 0, 0, 1);
    }

    static Store begin() {
        Store store = new Store(GlStateManager._getInteger(GL11.GL_UNPACK_ROW_LENGTH),
                GlStateManager._getInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                GlStateManager._getInteger(GL11.GL_UNPACK_SKIP_ROWS),
                GlStateManager._getInteger(GL11.GL_UNPACK_ALIGNMENT),
                GlStateManager._getInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
        Params clean = params();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, clean.rowLength());
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, clean.skipPixels());
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, clean.skipRows());
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, clean.alignment());
        if (store.unpackBuffer() != 0) {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        return store;
    }

    static void end(Store store) {
        if (store.unpackBuffer() != 0) {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, store.unpackBuffer());
        }
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, store.rowLength());
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, store.skipPixels());
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, store.skipRows());
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, store.alignment());
    }

    static boolean fits(ByteBuffer pixels, int width, int height) {
        return pixels != null && pixels.isDirect() && pixels.position() == 0
                && pixels.remaining() == width * height;
    }
}
