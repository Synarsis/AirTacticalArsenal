package com.synarsis.airtacticalarsenal.mixins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Mixin registration")
class MixinRegistrationTest {

    private static final Path JAVA = Path.of("src", "main", "java", "com", "synarsis", "airtacticalarsenal", "mixins");
    private static final Path CONFIG = Path.of("src", "main", "resources", "ata.mixins.json");

    private static Set<String> names(JsonObject json, String key) {
        Set<String> out = new TreeSet<>();
        if (json.has(key)) {
            JsonArray array = json.getAsJsonArray(key);
            for (JsonElement e : array) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    @Test
    @DisplayName("every mixin class is listed in ata.mixins.json, under mixins or client, and nothing listed is missing")
    void everyMixinIsRegistered() throws IOException {
        Set<String> classes = new TreeSet<>();
        try (Stream<Path> files = Files.list(JAVA)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".java") && !n.equals("AtaMixinPlugin.java"))
                    .forEach(n -> classes.add(n.substring(0, n.length() - ".java".length())));
        }
        JsonObject json = JsonParser.parseString(Files.readString(CONFIG, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> listed = new TreeSet<>(names(json, "mixins"));
        listed.addAll(names(json, "client"));
        listed.addAll(names(json, "server"));

        Set<String> missing = new TreeSet<>(classes);
        missing.removeAll(listed);
        assertTrue(missing.isEmpty(), "written but not in ata.mixins.json (they would silently not apply): " + missing);

        Set<String> ghosts = new TreeSet<>(listed);
        ghosts.removeAll(classes);
        assertTrue(ghosts.isEmpty(), "listed in ata.mixins.json but no such class: " + ghosts);
        assertTrue(classes.size() >= 5, "found " + classes);
        assertEquals(classes, listed);
    }

    @Test
    @DisplayName("a mixin that touches the client is listed under client, so a dedicated server never loads it")
    void clientMixinsAreClientOnly() throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(CONFIG, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> common = names(json, "mixins");
        try (Stream<Path> files = Files.list(JAVA)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String name = p.getFileName().toString().replace(".java", "");
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (text.contains("net.minecraft.client.")) {
                    assertTrue(!common.contains(name), name + " names client classes but is in the common list");
                }
            }
        }
    }
}
