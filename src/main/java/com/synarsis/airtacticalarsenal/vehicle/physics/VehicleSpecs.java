package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public final class VehicleSpecs {

    public static final String TEL = "iskander_tel";
    public static final String PATRIOT_LAUNCHER_TRAILER = "patriot_launcher_trailer";
    public static final String PATRIOT_RADAR_TRAILER = "patriot_radar_trailer";
    public static final String M983_TRACTOR = "m983_tractor";

    private static final List<String> STAGE_SEVEN_FILES = List.of("patriot_launcher", "patriot_radar");

    public static final String FILE_NAME = TEL + ".json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final class Entry<T> {
        final String name;
        final Supplier<T> defaults;
        final Function<String, T> parser;
        final Function<T, String> writer;
        final ToIntFunction<T> versionOf;
        final int currentVersion;
        T current;
        int revision;

        Entry(String name, Supplier<T> defaults, Function<String, T> parser, Function<T, String> writer,
                ToIntFunction<T> versionOf, int currentVersion) {
            this.name = name;
            this.defaults = defaults;
            this.parser = parser;
            this.writer = writer;
            this.versionOf = versionOf;
            this.currentVersion = currentVersion;
            this.current = defaults.get();
        }

        String fileName() {
            return this.name + ".json";
        }

        void use(T value) {
            this.current = value;
            this.revision++;
        }
    }

    private static final Map<String, Entry<?>> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put(TEL, new Entry<>(TEL, VehicleSpec::defaults, VehicleSpec::parse, VehicleSpec::toJson,
                s -> s.defaultsVersion, VehicleSpec.DEFAULTS_VERSION));
        ENTRIES.put(PATRIOT_LAUNCHER_TRAILER, trainEntry(PATRIOT_LAUNCHER_TRAILER));
        ENTRIES.put(PATRIOT_RADAR_TRAILER, trainEntry(PATRIOT_RADAR_TRAILER));
        ENTRIES.put(M983_TRACTOR, new Entry<>(M983_TRACTOR, TrainSpec::m983TractorDefaults,
                VehicleSpec::parse, VehicleSpec::toJson, s -> s.defaultsVersion, TrainSpec.M983_DEFAULTS_VERSION));
    }

    private static Entry<TrainSpec> trainEntry(String name) {
        return new Entry<>(name, () -> TrainSpec.defaultsFor(name), json -> TrainSpec.parse(name, json),
                TrainSpec::toJson, s -> s.defaultsVersion, TrainSpec.DEFAULTS_VERSION);
    }

    private VehicleSpecs() {
    }

    public static List<String> names() {
        return new ArrayList<>(ENTRIES.keySet());
    }

    @SuppressWarnings("unchecked")
    private static synchronized <T> Entry<T> entry(String name) {
        Entry<?> found = ENTRIES.get(name);
        if (found == null) {
            throw new IllegalArgumentException("unknown vehicle settings: " + name);
        }
        return (Entry<T>) found;
    }

    public static synchronized VehicleSpec current() {
        Entry<VehicleSpec> e = entry(TEL);
        return e.current;
    }

    public static synchronized int revision() {
        return entry(TEL).revision;
    }

    public static synchronized VehicleSpec m983Tractor() {
        Entry<VehicleSpec> e = entry(M983_TRACTOR);
        return e.current;
    }

    public static synchronized String currentJson() {
        return json(TEL);
    }

    public static synchronized TrainSpec train(String name) {
        Entry<TrainSpec> e = entry(name);
        return e.current;
    }

    public static synchronized int revision(String name) {
        return entry(name).revision;
    }

    public static synchronized String json(String name) {
        Entry<Object> e = entry(name);
        return writeAny(e);
    }

    @SuppressWarnings("unchecked")
    private static <T> String writeAny(Entry<T> e) {
        return e.writer.apply(e.current);
    }

    public static Path file(Path configDir) {
        return file(configDir, TEL);
    }

    public static Path file(Path configDir, String name) {
        return configDir.resolve("ata").resolve("vehicles").resolve(name + ".json");
    }

    public static synchronized String loadFromDisk(Path configDir) {
        List<String> problems = new ArrayList<>();
        retireStageSevenFiles(configDir);
        for (String name : names()) {
            String problem = loadOne(configDir, name);
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    private static void retireStageSevenFiles(Path configDir) {
        for (String name : STAGE_SEVEN_FILES) {
            Path file = file(configDir, name);
            try {
                if (Files.isRegularFile(file)) {
                    Files.move(file, file.resolveSibling(name + ".json.old"), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Vehicle settings {} belong to the tractor-and-trailer vehicle of stage 7: kept as {}, "
                            + "the trailers now have their own files", file, name + ".json.old");
                }
            } catch (IOException error) {
                LOGGER.warn("Vehicle settings {} could not be set aside: {}", file, error.getMessage());
            }
        }
    }

    private static <T> String loadOne(Path configDir, String name) {
        Entry<T> e = entry(name);
        Path file = file(configDir, name);
        try {
            if (!Files.isRegularFile(file)) {
                writeDefaults(e, file);
                return null;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            T parsed = e.parser.apply(text);
            if (e.versionOf.applyAsInt(parsed) < e.currentVersion) {
                Path old = file.resolveSibling(e.fileName() + ".old");
                Files.move(file, old, StandardCopyOption.REPLACE_EXISTING);
                writeDefaults(e, file);
                LOGGER.info("Vehicle settings {} were made for older defaults (version {}, now {}): kept as {}, new defaults written",
                        file, e.versionOf.applyAsInt(parsed), e.currentVersion, old.getFileName());
                return null;
            }
            e.use(parsed);
            return null;
        } catch (JsonParseException | IOException error) {
            String message = file + ": " + error.getMessage();
            LOGGER.warn("Vehicle settings were not read, the previous ones stay: {}", message);
            return message;
        }
    }

    private static <T> void writeDefaults(Entry<T> e, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        T defaults = e.defaults.get();
        Files.writeString(file, e.writer.apply(defaults), StandardCharsets.UTF_8);
        e.use(defaults);
    }

    public static synchronized void apply(String json) {
        apply(TEL, json);
    }

    public static synchronized void apply(String name, String json) {
        applyTo(entry(name), json);
    }

    private static <T> void applyTo(Entry<T> e, String json) {
        e.use(e.parser.apply(json));
    }

    public static synchronized void useDefaults() {
        for (String name : names()) {
            useDefaults(name);
        }
    }

    public static synchronized void useDefaults(String name) {
        useDefaultsOf(entry(name));
    }

    private static <T> void useDefaultsOf(Entry<T> e) {
        e.use(e.defaults.get());
    }
}
