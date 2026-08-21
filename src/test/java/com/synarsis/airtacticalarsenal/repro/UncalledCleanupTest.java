package com.synarsis.airtacticalarsenal.repro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REPRO: perf F-03 — the only routine that releases force-loaded chunks has no caller.
 *
 * <p>Each method below was written to solve a real problem and is complete. None of them has a
 * caller, which makes the problem it solves entirely unsolved while the code reads as if it were
 * handled.
 *
 * <p>The consequences are not symmetrical with ordinary dead code:
 * <ul>
 *   <li>{@code MissileChunkManager.cleanup} is the only thing that would release force-loaded
 *       chunks at shutdown. {@code ServerLevel.setChunkForced} writes into the level's persistent
 *       forced-chunk set, so chunks held by a missile in flight when the server stops stay forced
 *       in the save file — across restarts, with no in-game way to notice or undo it.</li>
 *   <li>{@code CreateCompat.registerBlockProtection} is what marks the mod's blocks immovable to
 *       Create contraptions. Uncalled, its own "could not register" warning never prints either,
 *       so the silence looks like success.</li>
 *   <li>The client sound registries are never cleared, so a looping preparing-sound whose stop
 *       packet never arrives keeps playing across worlds.</li>
 * </ul>
 *
 * <p>This scans source text rather than bytecode because the claim is about the whole codebase
 * ("nothing calls this"), not about one class's constant pool.
 */
@DisplayName("F-03: missile chunk cleanup has no caller")
class UncalledCleanupTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** Counts call sites of {@code method}, ignoring the file that declares it. */
    private static int callSitesOutside(String declaringFileName, String method) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            List<Path> candidates = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals(declaringFileName))
                    .toList();

            int hits = 0;
            for (Path p : candidates) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String code = line.trim();
                    if (code.startsWith("*") || code.startsWith("//")) {
                        continue; // javadoc and comments mention these methods; they are not calls
                    }
                    if (code.contains(method + "(")) {
                        hits++;
                    }
                }
            }
            return hits;
        }
    }

    private static void assertHasCaller(String declaringFile, String method, String consequence)
            throws IOException {
        int callers = callSitesOutside(declaringFile, method);
        assertTrue(callers > 0,
                method + "() is declared in " + declaringFile + " and called from nowhere in "
                        + SOURCE_ROOT + " (comments and javadoc excluded). " + consequence);
    }

    @Test
    void missileChunkCleanupMustBeWiredToShutdown() throws IOException {
        assertHasCaller("MissileChunkManager.java", "MissileChunkManager.cleanup",
                "It is the only code that releases the chunks missiles force-load."
                        + " ServerLevel.setChunkForced writes to the level's persistent forced-chunk"
                        + " set, so a missile airborne when the server stops leaves its chunks"
                        + " forced in the save file permanently -- they reload forced on the next"
                        + " start and keep ticking. No ServerStopping or LevelEvent.Unload"
                        + " subscriber exists anywhere in the mod to call this.");
    }

}
