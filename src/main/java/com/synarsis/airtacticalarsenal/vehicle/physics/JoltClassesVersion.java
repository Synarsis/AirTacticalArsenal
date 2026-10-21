package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Jolt;

import java.net.URL;
import java.security.CodeSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JoltClassesVersion {

    private static final Pattern JAR_NAME = Pattern.compile("(?i)jolt-?jni[^/\\\\!]*?-(\\d+\\.\\d+(?:\\.\\d+)?)(?:-[A-Za-z]+)*\\.jar");

    private JoltClassesVersion() {
    }

    static String versionIn(String location) {
        if (location == null) {
            return null;
        }
        Matcher matcher = JAR_NAME.matcher(location);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found;
    }

    static boolean agrees(String found, String expected) {
        return found == null || found.equals(expected);
    }

    static String ofLoadedClasses() {
        String fromSource = versionIn(sourceLocation());
        if (fromSource != null) {
            return fromSource;
        }
        URL resource = Jolt.class.getResource("Jolt.class");
        return resource == null ? null : versionIn(resource.toString());
    }

    private static String sourceLocation() {
        CodeSource source = Jolt.class.getProtectionDomain().getCodeSource();
        return source == null || source.getLocation() == null ? null : source.getLocation().toString();
    }
}
