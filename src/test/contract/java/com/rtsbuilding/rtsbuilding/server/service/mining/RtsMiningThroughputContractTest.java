package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMiningThroughputContractTest {
    @Test
    void batchMiningUsesTheRaisedDefaultWithoutEscapingSchedulerFairness() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));

        assertEquals(16, RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK);
        assertTrue(config.contains(
                "defineInRange(\"mining.ultimineBlocksPerTick\", 16, 1, 128)"));
        assertTrue(config.contains(
                "defineInRange(\"taskEngine.maxUnitsPerSlice\", 32, 1, 512)"));
        assertTrue(RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK < 32,
                "批量挖掘仍应低于单玩家切片预算，为公平轮转和其他任务保留余量");
    }
}
