package com.rtsbuilding.rtsbuilding.common.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 诊断 writer 的轻量源码回归入口，覆盖批写、轮转和 fail-open 写入失败计数。
 * 只验证通用 common writer 契约，不启动 Minecraft、Forge 或后台游戏进程。
 */
class RtsAsyncJsonlWriterTest {
    @TempDir
    Path tempDir;

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
        RtsAsyncJsonlWriter.append(file,
                "x".repeat((int) RtsAsyncJsonlWriter.MAX_FILE_BYTES) + "\n");
        assertTrue(RtsAsyncJsonlWriter.flush(Duration.ofSeconds(5)));

        RtsAsyncJsonlWriter.append(file, "tail\n");
        assertTrue(RtsAsyncJsonlWriter.flush(Duration.ofSeconds(5)));

        assertTrue(Files.isRegularFile(file.resolveSibling("rotate.jsonl.2")));
        assertEquals("tail\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void writeFailureIsCountedAndFlushStillCompletes() throws Exception {
        long before = RtsAsyncJsonlWriter.writeFailures();
        Path directory = Files.createDirectory(tempDir.resolve("not-a-file"));

        RtsAsyncJsonlWriter.append(directory, "{}\n");

        assertTrue(RtsAsyncJsonlWriter.flush(Duration.ofSeconds(5)));
        assertTrue(RtsAsyncJsonlWriter.writeFailures() > before);
    }
}
