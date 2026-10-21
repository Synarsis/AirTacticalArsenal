package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VehicleSpecs {

    public static final String FILE_NAME = "iskander_tel.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static VehicleSpec current = VehicleSpec.defaults();
    private static int revision;

    private VehicleSpecs() {
    }

    public static synchronized VehicleSpec current() {
        return current;
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized String currentJson() {
        return current.toJson();
    }

    public static Path file(Path configDir) {
        return configDir.resolve("ata").resolve("vehicles").resolve(FILE_NAME);
    }

    public static synchronized String loadFromDisk(Path configDir) {
        Path file = file(configDir);
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, VehicleSpec.defaults().toJson(), StandardCharsets.UTF_8);
                use(VehicleSpec.defaults());
                return null;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            VehicleSpec parsed = VehicleSpec.parse(text);
            use(parsed);
            return null;
        } catch (JsonParseException | IOException error) {
            String message = file + ": " + error.getMessage();
            LOGGER.warn("Vehicle settings were not read, the previous ones stay: {}", message);
            return message;
        }
    }

    public static synchronized void apply(String json) {
        use(VehicleSpec.parse(json));
    }

    public static synchronized void useDefaults() {
        use(VehicleSpec.defaults());
    }

    private static void use(VehicleSpec spec) {
        current = spec;
        revision++;
    }
}
