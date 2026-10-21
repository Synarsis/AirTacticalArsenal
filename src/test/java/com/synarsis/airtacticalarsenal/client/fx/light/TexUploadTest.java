package com.synarsis.airtacticalarsenal.client.fx.light;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Texture uploads do not inherit the pixel store state of vanilla")
class TexUploadTest {

    @Test
    @DisplayName("the upload parameters are always zeros with alignment one")
    void paramsAreClean() {
        TexUpload.Params params = TexUpload.params();
        assertEquals(0, params.rowLength());
        assertEquals(0, params.skipPixels());
        assertEquals(0, params.skipRows());
        assertEquals(1, params.alignment());
    }

    @Test
    @DisplayName("only a direct buffer starting at zero with exactly w*h bytes is uploaded")
    void fits() {
        assertTrue(TexUpload.fits(ByteBuffer.allocateDirect(256), 16, 16));
        assertFalse(TexUpload.fits(ByteBuffer.allocateDirect(255), 16, 16));
        assertFalse(TexUpload.fits(ByteBuffer.allocate(256), 16, 16));
        ByteBuffer shifted = ByteBuffer.allocateDirect(256);
        shifted.position(1);
        assertFalse(TexUpload.fits(shifted, 16, 16));
        assertFalse(TexUpload.fits(null, 16, 16));
    }
}
