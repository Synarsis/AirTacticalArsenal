package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Jolt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Jolt runtime")
class JoltRuntimeTest {

    @Test
    @DisplayName("the bundled native library loads on this machine, in double precision")
    void loads() throws Exception {
        Path root = Path.of("build", "test-natives");
        assertTrue(JoltRuntime.load(root), "the native library did not load");
        assertTrue(JoltRuntime.available());
        assertTrue(Jolt.isDoublePrecision());
        assertFalse(JoltRuntime.version().isEmpty());
        assertTrue(Files.isRegularFile(root.resolve(JoltRuntime.version()).resolve(JoltRuntime.libraryFileName())));
        assertEquals(JoltRuntime.version(), "6.2.0");
    }

    @Test
    @DisplayName("platform folders are what the build extracts")
    void platform() {
        String folder = JoltRuntime.platformFolder();
        assertTrue(java.util.List.of("windows-x86_64", "linux-x86_64", "linux-aarch64", "macos-x86_64", "macos-aarch64").contains(folder));
        assertNotNullResource(folder);
    }

    private static void assertNotNullResource(String folder) {
        assertTrue(JoltRuntime.class.getResource("/ata_natives/" + folder + "/" + JoltRuntime.libraryFileName()) != null);
    }
}
