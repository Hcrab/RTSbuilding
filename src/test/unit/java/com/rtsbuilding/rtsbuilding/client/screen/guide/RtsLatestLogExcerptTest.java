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

    @Test
    void fallsBackFromWrongLauncherDirectoryToCanonicalGameDirectory() throws IOException {
        Path actualLog = tempDir.resolve("actual-game").resolve("logs").resolve("latest.log");
        Files.createDirectories(actualLog.getParent());
        Files.writeString(actualLog,
                "[Render thread/INFO] [com.rtsbuilding.rtsbuilding/] copied from canonical game directory",
                StandardCharsets.UTF_8);

        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.readFirstAvailable(
                tempDir.resolve("launcher-working-directory").resolve("logs").resolve("latest.log"),
                actualLog);

        assertTrue(result.available());
        assertTrue(result.latestLines().contains("canonical game directory"));
    }

    @Test
    void malformedUtf8FromThirdPartyModsDoesNotHideAnOtherwiseReadableLog() throws IOException {
        Path log = tempDir.resolve("latest.log");
        byte[] prefix = "[Render thread/INFO] before bad bytes ".getBytes(StandardCharsets.UTF_8);
        byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28};
        byte[] suffix = "\n[Server thread/INFO] [rtsbuilding] operation completed"
                .getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[prefix.length + invalidUtf8.length + suffix.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(invalidUtf8, 0, content, prefix.length, invalidUtf8.length);
        System.arraycopy(suffix, 0, content, prefix.length + invalidUtf8.length, suffix.length);
        Files.write(log, content);

        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.read(log);

        assertTrue(result.available());
        assertTrue(result.latestLines().contains("before bad bytes"));
        assertTrue(result.rtsLines().contains("operation completed"));
    }

    @Test
    void unavailableCandidatesRemainAValidNoLogResult() {
        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.readFirstAvailable(
                null,
                tempDir.resolve("missing-a.log"),
                tempDir.resolve("missing-b.log"));

        assertFalse(result.available());
    }
}
