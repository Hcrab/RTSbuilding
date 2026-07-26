package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止一次指定工具请求把后续空手范围挖掘永久锁死。
 */
class MiningToolSelectionStateContractTest {

    @Test
    void nonQueuedRequestsReplaceTheWholeToolSelectionSnapshot() throws IOException {
        String single = source("MiningExecutePipe.java");
        String batch = source("UltimineExecutePipe.java");

        assertTrue(single.contains(
                "session.mining.miningSelectedToolRequested = mctx.isSelectedToolRequested();"));
        assertTrue(batch.contains(
                "session.mining.miningSelectedToolRequested = mctx.isSelectedToolRequested();"));
        assertFalse(single.contains(
                "if (mctx.isSelectedToolRequested()) {\n            session.mining.miningSelectedToolRequested = true;"));
        assertFalse(batch.contains(
                "if (mctx.isSelectedToolRequested()) {\n            session.mining.miningSelectedToolRequested = true;"));
    }

    @Test
    void emptyNonQueuedBatchAlsoClearsStaleToolSelection() throws IOException {
        String batch = source("UltimineExecutePipe.java");
        assertTrue(batch.contains("completeWithoutTask(mctx, session, queueMode)"));
        assertTrue(batch.contains("if (queueMode) {\n            return;\n        }"));
        assertTrue(batch.contains("session.mining.miningSelectedToolRequested = false;"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/mining", name))
                .replace("\r\n", "\n");
    }
}
