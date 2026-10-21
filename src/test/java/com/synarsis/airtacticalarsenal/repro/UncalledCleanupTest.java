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

@DisplayName("F-03: missile chunk cleanup has no caller")
class UncalledCleanupTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

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
                        continue;
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
