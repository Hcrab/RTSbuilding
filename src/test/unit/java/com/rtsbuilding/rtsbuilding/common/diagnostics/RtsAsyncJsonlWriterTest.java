package com.rtsbuilding.rtsbuilding.common.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RtsAsyncJsonlWriterTest {
  @TempDir Path tempDir;

  @Test
  void batchesAndFlushesWithoutLosingLines() throws Exception {
    Path file = tempDir.resolve("diagnostics.jsonl");
    for (int i = 0; i < 100; i++) {
      RtsAsyncJsonlWriter.append(file, "{\"line\":" + i + "}\n");
    }
    assertTrue(RtsAsyncJsonlWriter.flush(Duration.ofSeconds(5)));
    assertEquals(100, Files.readAllLines(file, StandardCharsets.UTF_8).size());
  }

  @Test
  void rotatesOversizedFileAndKeepsCurrentFileWritable() throws Exception {
    Path file = tempDir.resolve("rotate.jsonl");
    RtsAsyncJsonlWriter.append(file, "x".repeat((int) RtsAsyncJsonlWriter.MAX_FILE_BYTES) + "\n");
    assertTrue(RtsAsyncJsonlWriter.flush(Duration.ofSeconds(5)));
    RtsAsyncJsonlWriter.append(file, "tail\n");
    assertTrue(RtsAsyncJsonlWriter.flush(Duration.ofSeconds(5)));
    assertTrue(Files.isRegularFile(file.resolveSibling("rotate.jsonl.2")));
    assertEquals("tail\n", Files.readString(file, StandardCharsets.UTF_8));
  }
}
