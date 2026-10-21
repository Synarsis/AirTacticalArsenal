package com.synarsis.airtacticalarsenal.repro.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("F-4: registered blocks have no loot table and are destroyed on break")
class BlockLootTableCoverageTest {

    private static final Path MOD_BLOCKS = Path.of("src", "main", "java", "com", "synarsis",
            "airtacticalarsenal", "block", "ModBlocks.java");
    private static final Path LOOT_TABLE_DIR =
            Path.of("src", "main", "resources", "data", "ata", "loot_table", "blocks");

    private static final Pattern REGISTRATION =
            Pattern.compile("registerBlock(?:NoItem)?\\s*\\(\\s*\"([a-z0-9_]+)\"");
    private static final Pattern DECLARED_DROPLESS =
            Pattern.compile("registerBlock(?:NoItem)?\\s*\\(\\s*\"([a-z0-9_]+)\"[^;]*?noLootTable\\(\\)",
                    Pattern.DOTALL);

    private static String modBlocksSource() throws IOException {
        return Files.readString(MOD_BLOCKS, StandardCharsets.UTF_8);
    }

    private static Set<String> registeredBlockNames(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = REGISTRATION.matcher(source);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Set<String> droplessByDeclaration(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = DECLARED_DROPLESS.matcher(source);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    @Test
    void controlTheRegistrationsAreReadable() throws IOException {
        String source = modBlocksSource();
        Set<String> registered = registeredBlockNames(source);

        assertFalse(registered.isEmpty(), "no block registrations parsed out of " + MOD_BLOCKS);
        assertTrue(registered.containsAll(Set.of("shahed_radar", "siren", "shahed_terminal")),
                "expected the three blocks among parsed registrations, got " + registered);
        for (String name : Set.of("shahed_radar", "siren", "shahed_terminal")) {
            assertTrue(Files.exists(LOOT_TABLE_DIR.resolve(name + ".json")),
                    "expected the shipped " + name + " loot table at " + LOOT_TABLE_DIR);
        }
    }

    @Test
    void everyRegisteredBlockEitherHasALootTableOrIsDeclaredDropless() throws IOException {
        String source = modBlocksSource();
        Set<String> dropless = droplessByDeclaration(source);

        List<String> vanishing = new ArrayList<>();
        for (String name : registeredBlockNames(source)) {
            if (dropless.contains(name)) {
                continue;
            }
            if (!Files.exists(LOOT_TABLE_DIR.resolve(name + ".json"))) {
                vanishing.add(name);
            }
        }

        assertTrue(vanishing.isEmpty(),
                "These registered blocks have no loot table in " + LOOT_TABLE_DIR
                        + " and are not declared .noLootTable(): " + vanishing
                        + ". Each is craftable and each is annihilated when mined -- the loot"
                        + " manager cannot find 'ata:blocks/<name>', substitutes an empty table,"
                        + " and logs the miss every time.");
    }
}
