package com.synarsis.airtacticalarsenal.vehicle.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Which jolt-jni the game has loaded")
class JoltClassesVersionTest {

    @Test
    @DisplayName("the version is read from the name of the jar, however it is wrapped")
    void fromNames() {
        assertEquals("6.2.0", JoltClassesVersion.versionIn("file:/C:/Users/x/.gradle/jolt-jni-Windows64-6.2.0.jar"));
        assertEquals("6.3.0", JoltClassesVersion.versionIn("jar:file:/mods/other.jar%23123!/META-INF/jarjar/jolt-jni-Linux64-6.3.0.jar"));
        assertEquals("6.2.0", JoltClassesVersion.versionIn("union:/mods/ata.jar%2312!/META-INF/jarjar/jolt-jni-Windows64-6.2.0.jar!/"));
        assertEquals("7.0.1", JoltClassesVersion.versionIn("file:/x/joltjni-7.0.1.jar"));
        assertEquals("6.2", JoltClassesVersion.versionIn("file:/x/jolt-jni-MacOSX_ARM64-6.2.jar"));
        assertEquals("6.2.0", JoltClassesVersion.versionIn("file:/x/jolt-jni-Windows64-6.2.0-ReleaseDp.jar"));
        assertEquals("6.4.0", JoltClassesVersion.versionIn("jar:file:/mods/ata-0.1.0.jar!/META-INF/jarjar/a.jar!/jolt-jni-Windows64-6.4.0.jar"));
    }

    @Test
    @DisplayName("an unrelated jar or nothing at all gives no version")
    void unknown() {
        assertNull(JoltClassesVersion.versionIn("file:/x/geckolib-4.7.4.jar"));
        assertNull(JoltClassesVersion.versionIn("file:/x/ata-0.1.0-dev.jar"));
        assertNull(JoltClassesVersion.versionIn(""));
        assertNull(JoltClassesVersion.versionIn(null));
    }

    @Test
    @DisplayName("a different version is a mismatch, an unknown one is let through")
    void agreement() {
        assertTrue(JoltClassesVersion.agrees("6.2.0", "6.2.0"));
        assertFalse(JoltClassesVersion.agrees("6.3.0", "6.2.0"));
        assertFalse(JoltClassesVersion.agrees("6.2", "6.2.0"));
        assertTrue(JoltClassesVersion.agrees(null, "6.2.0"));
    }

    @Test
    @DisplayName("in the test run the loaded classes are the ones the natives were made for")
    void thisRun() {
        String found = JoltClassesVersion.ofLoadedClasses();
        assertEquals("6.2.0", found);
        assertTrue(JoltClassesVersion.agrees(found, "6.2.0"));
    }
}
