package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止一次指定工具请求把后续空手挖掘永久锁死。
 */
class MiningToolSelectionStateContractTest {

    @Test
    void eachSingleMiningRequestReplacesTheWholeToolSnapshot() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/mining/MiningExecutePipe.java"));

        assertTrue(source.contains("session.mining.miningToolLease = ctx.hasToolLease()"));
        assertTrue(source.contains("session.mining.miningSelectedToolRequested = ctx.isSelectedToolRequested();"));
    }
}
