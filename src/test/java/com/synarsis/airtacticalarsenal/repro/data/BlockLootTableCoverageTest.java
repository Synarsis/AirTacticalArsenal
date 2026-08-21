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

/**
 * REPRO: blocks F-4 — craftable blocks that drop nothing when broken.
 *
 * <p>A block with neither a loot table nor {@code .noLootTable()} resolves to a loot table id
 * that does not exist. The loot manager falls back to an empty table and logs the miss, so the
 * block silently vanishes on break.
 *
 * <p>The mod ships two loot tables, for {@code launcher} and {@code unified_terminal}, and marks
 * exactly one block exempt. Everything else registered is craftable from real resources and is
 * destroyed by mining it. Nothing fills the gap at build time: the project has no
 * {@code LootTableProvider} and no {@code src/generated}, so what is on disk is all there is.
 *
 * <p>The registered block names are read out of {@code ModBlocks.java} rather than from the
 * runtime registry, because touching {@code DeferredHolder} outside a running game throws while
 * initialising FML — which would make this test fail for a reason that has nothing to do with
 * loot tables. Reading the declarations keeps the failure attributable to the defect itself, and
 * still picks up any newly registered block.
 */
@DisplayName("F-4: registered blocks have no loot table and are destroyed on break")
class BlockLootTableCoverageTest {

    private static final Path MOD_BLOCKS = Path.of("src", "main", "java", "com", "synarsis",
            "airtacticalarsenal", "block", "ModBlocks.java");
    private static final Path LOOT_TABLE_DIR =
            Path.of("src", "main", "resources", "data", "ata", "loot_table", "blocks");

    /** Matches registerBlock("name", ...) and registerBlockNoItem("name", ...). */
    private static final Pattern REGISTRATION =
            Pattern.compile("registerBlock(?:NoItem)?\\s*\\(\\s*\"([a-z0-9_]+)\"");
    /** A registration whose properties chain declares the block intentionally dropless. */
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

        // If the parse breaks, every other assertion here becomes meaningless, so prove it first.
        assertFalse(registered.isEmpty(), "no block registrations parsed out of " + MOD_BLOCKS);
        assertTrue(registered.contains("launcher"),
                "expected 'launcher' among parsed registrations, got " + registered);
        assertTrue(droplessByDeclaration(source).contains("iskander_rocket"),
                "expected iskander_rocket to be the block declared .noLootTable(); parsed: "
                        + droplessByDeclaration(source));
        assertTrue(Files.exists(LOOT_TABLE_DIR.resolve("launcher.json")),
                "expected the shipped launcher loot table at " + LOOT_TABLE_DIR);
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
