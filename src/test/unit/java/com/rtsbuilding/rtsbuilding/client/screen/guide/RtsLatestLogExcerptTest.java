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
            lines.add("[Server thread/INFO] [com.rtsbuilding.rtsbuilding.RtsbuildingMod/] RTS line " + i);
        }
        Path log = tempDir.resolve("latest.log");
        Files.write(log, lines, StandardCharsets.UTF_8);

        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.read(log);

        assertTrue(result.available());
        assertEquals(200, result.latestLines().lines().count());
        assertEquals(200, result.rtsLines().lines().count());
        assertFalse(result.latestLines().contains("RTS line 1\n"));
        assertTrue(result.latestLines().endsWith("RTS line 259"));
        assertTrue(result.rtsLines().endsWith("RTS line 259"));
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
    void recognizesAllStructuredRtsPrefixes() throws IOException {
        Path log = tempDir.resolve("prefixes.log");
        Files.writeString(log,
                "[RTS-TRACE] event=INPUT_PRESS\n"
                        + "[RTS-DIAG] event=TERMINAL\n"
                        + "[RTS-SERVER-HEALTH] event=TICK_GAP\n",
                StandardCharsets.UTF_8);

        RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.read(log);

        assertEquals(3, result.rtsLines().lines().count());
        assertTrue(result.rtsLines().contains("[RTS-TRACE]"));
        assertTrue(result.rtsLines().contains("[RTS-DIAG]"));
        assertTrue(result.rtsLines().contains("[RTS-SERVER-HEALTH]"));
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
