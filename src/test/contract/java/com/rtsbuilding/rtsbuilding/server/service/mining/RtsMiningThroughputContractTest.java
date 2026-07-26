package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMiningThroughputContractTest {
    @Test
    void twoQueuedMiningBatchesHaveRoomForFullFairSlices() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));

        assertEquals(32, RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK);
        assertTrue(config.contains(
                "defineInRange(java.util.List.of(\"mining\", \"ultimineBlocksPerTick\"), 32, 1, 128)"));
        assertTrue(config.contains(
                "defineInRange(java.util.List.of(\"taskEngine\", \"maxUnitsPerSlice\"), 32, 1, 512)"));
        assertTrue(config.contains(
                "defineInRange(java.util.List.of(\"taskEngine\", \"maxNanosPerTick\"), 8_000_000L, 250_000L, 20_000_000L)"));
        assertEquals(32, RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK,
                "单个挖掘任务应能完整使用一次公平切片，不再浪费一半配额");
        assertTrue(RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK * 2 <= 256,
                "两批挖掘仍必须处于全局单位硬预算内");
    }
}
