package com.synarsis.airtacticalarsenal.entity.patriot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot language keys")
class PatriotLangKeysTest {

    private static final String[] FILES = {"en_us.json", "ru_ru.json"};

    private static JsonObject lang(String file) throws IOException {
        Path p = Path.of("src", "main", "resources", "assets", "ata", "lang", file);
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    @DisplayName("every refusal, status, button, title and item has text in both languages")
    void everythingHasText() throws IOException {
        for (String file : FILES) {
            JsonObject lang = lang(file);
            List<String> missing = new ArrayList<>();
            for (PatriotRefusal r : PatriotRefusal.values()) {
                if (r.ok()) {
                    continue;
                }
                if (!lang.has(r.messageKey())) {
                    missing.add(r.messageKey());
                }
            }
            for (String name : PatriotStatus.KEYS) {
                if (!lang.has(PatriotStatus.keyOf(name))) {
                    missing.add(PatriotStatus.keyOf(name));
                }
            }
            for (Command c : Command.values()) {
                String base = "gui.ata.patriot.button." + c.name().toLowerCase(Locale.ROOT);
                if (c == Command.LIFT_UP || c == Command.LIFT_DOWN) {
                    for (String suffix : new String[] {".launcher", ".radar"}) {
                        if (!lang.has(base + suffix)) {
                            missing.add(base + suffix);
                        }
                    }
                } else if (!lang.has(base)) {
                    missing.add(base);
                }
            }
            for (PatriotKind kind : PatriotKind.values()) {
                for (String key : new String[] {"item.ata." + kind.id(), "entity.ata." + kind.id(),
                        "item.ata." + kind.id() + ".tooltip.1", "item.ata." + kind.id() + ".tooltip.2",
                        "gui.ata.patriot.title." + kind.id()}) {
                    if (!lang.has(key)) {
                        missing.add(key);
                    }
                }
            }
            for (String key : new String[] {"gui.ata.patriot.hitch.coupled", "gui.ata.patriot.hitch.detached",
                    "gui.ata.patriot.can_drive", "gui.ata.patriot.cannot_drive", "gui.ata.patriot.traverse",
                    "message.ata.patriot.no_room", "message.ata.patriot.no_support", "message.ata.patriot.full",
                    "message.ata.patriot.righting", "message.ata.patriot.overturned", "message.ata.patriot.repaired",
                    "item.ata.patriot.tooltip.place", "item.ata.patriot.tooltip.health"}) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), file + " lacks " + missing);
        }
    }

    @Test
    @DisplayName("the two languages have the same Patriot keys, and the launcher and radar names are as agreed")
    void sameKeys() throws IOException {
        TreeSet<String> en = new TreeSet<>();
        TreeSet<String> ru = new TreeSet<>();
        for (String k : lang("en_us.json").keySet()) {
            if (k.contains("patriot")) {
                en.add(k);
            }
        }
        for (String k : lang("ru_ru.json").keySet()) {
            if (k.contains("patriot")) {
                ru.add(k);
            }
        }
        assertEquals(en, ru);
        assertEquals("Patriot launcher (M901, M983 tractor)", lang("en_us.json").get("item.ata.patriot_launcher").getAsString());
        assertEquals("AN/MPQ-65 radar (M983 tractor)", lang("en_us.json").get("item.ata.patriot_radar").getAsString());
        assertEquals("ПУ Patriot (M901, тягач M983)", lang("ru_ru.json").get("item.ata.patriot_launcher").getAsString());
        assertEquals("РЛС AN/MPQ-65 (тягач M983)", lang("ru_ru.json").get("item.ata.patriot_radar").getAsString());
    }
}
