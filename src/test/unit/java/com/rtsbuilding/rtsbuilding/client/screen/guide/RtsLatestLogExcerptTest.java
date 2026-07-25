package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsLatestLogExcerptTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsOnlyConfiguredTailWindows() throws IOException {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 260; i++) {
            lines.add(i % 3 == 0
                    ? "[Server thread/INFO] [com.rtsbuilding.rtsbuilding.RtsbuildingMod/] RTS line " + i
                    : "[Server thread/INFO] [minecraft/] General line " + i);
        }
        Path log = tempDir.resolve("latest.log");
        Files.write(log, lines, StandardCharsets.UTF_8);

        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.read(log);

        assertTrue(result.available());
        assertEquals(200, result.latestLines().lines().count());
        assertEquals(50, result.rtsLines().lines().count());
        assertFalse(result.latestLines().contains("General line 1\n"));
        assertTrue(result.latestLines().endsWith("General line 259"));
        assertTrue(result.rtsLines().endsWith("RTS line 258"));
    }

    @Test
    void missingLogReturnsUnavailableResult() {
        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.read(tempDir.resolve("missing.log"));

        assertFalse(result.available());
        assertTrue(result.latestLines().isEmpty());
        assertTrue(result.rtsLines().isEmpty());
    }
}
