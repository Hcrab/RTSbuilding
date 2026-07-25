package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsLatestLogExcerptTest {
    @Test
    void keepsBoundedLatestAndRtsSpecificTails() throws Exception {
        Path log = Files.createTempFile("rts-ai-help", ".log");
        try {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 230; i++) {
                text.append(i % 2 == 0 ? "[rtsbuilding] " : "[other] ")
                        .append(i).append('\n');
            }
            Files.writeString(log, text, StandardCharsets.UTF_8);

            RtsLatestLogExcerpt.Result result = RtsLatestLogExcerpt.read(log);

            assertTrue(result.available());
            assertEquals(200, result.latestLines().lines().count());
            assertEquals(50, result.rtsLines().lines().count());
            assertFalse(result.latestLines().contains("[rtsbuilding] 0\n"));
            assertTrue(result.rtsLines().contains("[rtsbuilding] 228"));
        } finally {
            Files.deleteIfExists(log);
        }
    }
}
