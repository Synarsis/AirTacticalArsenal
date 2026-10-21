package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Jolt;

import java.lang.module.ModuleDescriptor;
import java.net.URL;
import java.security.CodeSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JoltClassesVersion {

    record Evidence(String moduleName, String moduleVersion, String sourceLocation, String resourceUrl) {
        String describe() {
            return "module " + this.moduleName + " version " + this.moduleVersion + ", code source " + this.sourceLocation
                    + ", class resource " + this.resourceUrl;
        }
    }

    private static final Pattern JAR_NAME = Pattern.compile("(?i)jolt-?jni[^/\\\\!]*?-(\\d+\\.\\d+(?:\\.\\d+)?)(?:-[A-Za-z]+)*\\.jar");
    private static final Pattern LEADING_VERSION = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)");

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

    static String moduleVersionIn(String rawVersion) {
        if (rawVersion == null) {
            return null;
        }
        Matcher matcher = LEADING_VERSION.matcher(rawVersion.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    static String choose(Evidence evidence) {
        String fromModule = moduleVersionIn(evidence.moduleVersion());
        if (fromModule != null) {
            return fromModule;
        }
        String fromSource = versionIn(evidence.sourceLocation());
        if (fromSource != null) {
            return fromSource;
        }
        return versionIn(evidence.resourceUrl());
    }

    static boolean agrees(String found, String expected) {
        return found == null || found.equals(expected);
    }

    static Evidence gather() {
        Module module = Jolt.class.getModule();
        ModuleDescriptor descriptor = module.isNamed() ? module.getDescriptor() : null;
        String version = descriptor == null ? null : descriptor.rawVersion().orElse(null);
        CodeSource source = Jolt.class.getProtectionDomain().getCodeSource();
        String location = source == null || source.getLocation() == null ? null : source.getLocation().toString();
        URL resource = Jolt.class.getResource("Jolt.class");
        return new Evidence(module.isNamed() ? module.getName() : null, version, location,
                resource == null ? null : resource.toString());
    }

    static String ofLoadedClasses() {
        return choose(gather());
    }
}
