package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 后台建造取料/退款边界的静态契约；行为仍需在受控游戏矩阵中验证。 */
class ConstructionMaterialSourcesContractTest {
    @Test
    void sourceResolverUsesLinkedStorageAndPlayerMainInventoryOnly() throws IOException {
        String sources = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/ConstructionMaterialSources.java");

        assertTrue(sources.contains("getPlayerMainInventoryStart"));
        assertTrue(sources.contains("getPlayerMainInventoryEndExclusive"));
        assertTrue(sources.contains("extractSelectedFromNetworkCached"));
        assertTrue(sources.contains("RtsTransferInserter.refundToLinked"));
        assertTrue(sources.contains("ItemStack.isSameItemSameTags"));
        assertTrue(sources.contains("sourceKinds"));

        assertFalse(sources.contains("containerMenu"));
        assertFalse(sources.contains("getCarried"));
        assertFalse(sources.contains("getArmor"));
        assertFalse(sources.contains("shouldIncludePlayerMainInventoryInStorageView"));
    }

    @Test
    void executorKeepsRealExtractedStackRemainderAndRefundPath() throws IOException {
        String executor = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java");

        assertTrue(executor.contains("ConstructionMaterialSources.resolve"));
        assertTrue(executor.contains("ConstructionMaterialSources.extractOne"));
        assertTrue(executor.contains("finalOutcome.remainder()"));
        assertTrue(executor.contains("RtsTransferInserter.refundToLinked"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
