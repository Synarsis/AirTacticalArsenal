package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.NativeLibraryLoader;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public final class JoltRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_ROOT = "/ata_natives/";

    private static boolean attempted;
    private static boolean ready;
    private static String version = "";

    private JoltRuntime() {
    }

    public static synchronized boolean available() {
        if (!attempted) {
            load(defaultRoot());
        }
        return ready;
    }

    public static synchronized String version() {
        return version;
    }

    public static synchronized boolean load(Path nativesRoot) {
        if (attempted) {
            return ready;
        }
        attempted = true;
        try {
            String platform = platformFolder();
            String library = libraryFileName();
            String wrapperVersion = readVersion();
            String classes = JoltClassesVersion.ofLoadedClasses();
            if (!JoltClassesVersion.agrees(classes, wrapperVersion)) {
                throw new JoltUnavailable("the Jolt classes in the game are jolt-jni " + classes
                        + " (probably from another mod) but the bundled native library is " + wrapperVersion);
            }
            if (classes == null) {
                LOGGER.warn("The version of the Jolt classes in the game could not be read, assuming jolt-jni {}", wrapperVersion);
            }
            if (nativesAlreadyLinked()) {
                throw new JoltUnavailable("the Jolt native library was already loaded by another mod");
            }
            Path target = nativesRoot.resolve(wrapperVersion).resolve(library);
            extract(RESOURCE_ROOT + platform + "/" + library, target);
            if (!NativeLibraryLoader.loadLibrary(target.toAbsolutePath().toString())) {
                throw new UnsatisfiedLinkError("the native library did not load from " + target);
            }
            Jolt.registerDefaultAllocator();
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();
            if (!Jolt.newFactory()) {
                throw new IllegalStateException("Jolt factory was not created");
            }
            Jolt.registerTypes();
            if (!Jolt.isDoublePrecision()) {
                throw new IllegalStateException("the loaded Jolt is not the double precision build");
            }
            version = wrapperVersion;
            ready = true;
            LOGGER.info("Jolt Physics loaded: jolt-jni {} ({}), {}", wrapperVersion, Jolt.versionString(), platform);
        } catch (Throwable error) {
            ready = false;
            LOGGER.warn("Jolt Physics is not available, vehicles use the built-in physics: {}",
                    error instanceof JoltUnavailable ? error.getMessage() : error.toString());
        }
        return ready;
    }

    private static final class JoltUnavailable extends RuntimeException {
        JoltUnavailable(String message) {
            super(message);
        }
    }

    private static boolean nativesAlreadyLinked() {
        try {
            Jolt.buildType();
            return true;
        } catch (UnsatisfiedLinkError notLinked) {
            return false;
        }
    }

    static synchronized void forgetForTests() {
        attempted = false;
        ready = false;
    }

    private static Path defaultRoot() {
        Path game = null;
        try {
            game = FMLPaths.GAMEDIR.get();
        } catch (Throwable ignored) {
        }
        if (game == null) {
            return Path.of(System.getProperty("java.io.tmpdir"), "ata-natives");
        }
        return game.resolve("ata").resolve("natives");
    }

    static String platformFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
        if (!arm && !x64) {
            throw new UnsupportedOperationException("unsupported processor " + arch);
        }
        String cpu = arm ? "aarch64" : "x86_64";
        if (os.contains("win")) {
            if (arm) {
                throw new UnsupportedOperationException("Windows on ARM is not supported");
            }
            return "windows-" + cpu;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos-" + cpu;
        }
        if (os.contains("linux")) {
            return "linux-" + cpu;
        }
        throw new UnsupportedOperationException("unsupported system " + os);
    }

    static String libraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "joltjni.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libjoltjni.dylib";
        }
        return "libjoltjni.so";
    }

    private static String readVersion() throws IOException {
        try (InputStream in = JoltRuntime.class.getResourceAsStream(RESOURCE_ROOT + "version.txt")) {
            if (in == null) {
                throw new IOException("the bundled native library list is missing");
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
    }

    private static void extract(String resource, Path target) throws IOException {
        byte[] bytes;
        try (InputStream in = JoltRuntime.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("no bundled native library at " + resource);
            }
            bytes = in.readAllBytes();
        }
        if (Files.isRegularFile(target) && Files.size(target) == bytes.length && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.write(part, bytes);
        try {
            Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException locked) {
            Files.deleteIfExists(part);
            if (!Files.isRegularFile(target)) {
                throw locked;
            }
        }
    }
}
