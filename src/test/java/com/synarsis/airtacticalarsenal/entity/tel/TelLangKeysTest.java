package com.synarsis.airtacticalarsenal.entity.tel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Launcher language keys")
class TelLangKeysTest {

    private static final Path ROOT = Path.of("src", "main");
    private static final String[] FILES = {"en_us.json", "ru_ru.json"};

    private static JsonObject lang(String file) throws IOException {
        Path p = ROOT.resolve("resources/assets/ata/lang").resolve(file);
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String allJava() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(ROOT.resolve("java"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                all.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return all.toString();
    }

    @Test
    @DisplayName("every refusal, every state and every clip's message has text in both languages")
    void everythingHasText() throws IOException {
        for (String file : FILES) {
            JsonObject lang = lang(file);
            List<String> missing = new ArrayList<>();
            for (Refusal r : Refusal.values()) {
                if (!r.ok() && !lang.has(r.messageKey())) {
                    missing.add(r.messageKey());
                }
            }
            for (TelState s : TelState.values()) {
                String key = "gui.ata.tel.state." + s.name().toLowerCase(Locale.ROOT);
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            for (String key : new String[] {"gui.ata.tel.error.too_close", "gui.ata.tel.error.too_far",
                    "gui.ata.tel.error.forbidden", "key.ata.tel_fire_control", "message.ata.tel.target_set",
                    "message.ata.tel.launch_started", "message.ata.tel.aborted", "message.ata.tel.load_from_cab"}) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), file + " lacks " + missing);
        }
    }

    @Test
    @DisplayName("the two languages have the same launcher keys")
    void sameKeys() throws IOException {
        TreeSet<String> en = new TreeSet<>();
        TreeSet<String> ru = new TreeSet<>();
        for (String k : lang("en_us.json").keySet()) {
            if (k.contains(".tel")) {
                en.add(k);
            }
        }
        for (String k : lang("ru_ru.json").keySet()) {
            if (k.contains(".tel")) {
                ru.add(k);
            }
        }
        TreeSet<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(ru);
        TreeSet<String> onlyRu = new TreeSet<>(ru);
        onlyRu.removeAll(en);
        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(), "only in English " + onlyEn + ", only in Russian " + onlyRu);
    }

    @Test
    @DisplayName("no console or message text is left that nothing uses")
    void nothingUnused() throws IOException {
        String java = allJava();
        for (String file : FILES) {
            List<String> unused = new ArrayList<>();
            for (String key : lang(file).keySet()) {
                boolean console = key.startsWith("gui.ata.tel.") || key.startsWith("message.ata.tel.");
                if (!console) {
                    continue;
                }
                boolean literal = java.contains("\"" + key + "\"");
                boolean dynamicState = key.startsWith("gui.ata.tel.state.") || key.startsWith("gui.ata.tel.error.");
                boolean refusal = false;
                for (Refusal r : Refusal.values()) {
                    refusal |= r.messageKey().equals(key);
                }
                if (!literal && !dynamicState && !refusal) {
                    unused.add(key);
                }
            }
            assertTrue(unused.isEmpty(), file + " has unused keys " + unused);
        }
    }
}
