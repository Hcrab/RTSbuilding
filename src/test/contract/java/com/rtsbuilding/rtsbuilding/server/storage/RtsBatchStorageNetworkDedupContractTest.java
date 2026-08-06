package com.rtsbuilding.rtsbuilding.server.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBatchStorageNetworkDedupContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void batchDiscoveryDeduplicatesNetworksBeforeCreatingHandlers() throws IOException {
        String batch = Files.readString(ROOT.resolve(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/bindings/RtsBatchStorageBindingService.java"));

        assertTrue(batch.contains("RtsAe2Compat.probeBatchNetwork"));
        assertTrue(batch.contains("RtsRefinedStorageCompat.probeBatchNetwork"));
        assertTrue(batch.contains("networkCandidates.merge"));
        assertTrue(batch.contains("preferTerminal"));
        assertTrue(batch.contains("collectExistingNetworks"));
        assertFalse(batch.contains("RtsLinkedCapabilities.findLinkedItemHandler"),
                "批量发现阶段不能为每个网络节点创建完整库存快照");
    }

    @Test
    void linkedStorageLimitComesFromServerConfig() throws IOException {
        String config = Files.readString(ROOT.resolve(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String binding = Files.readString(ROOT.resolve(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/bindings/RtsLinkedStorageBindingService.java"));

        assertTrue(config.contains("storage.maxLinkedStorages\", 200, 1, 4096"));
        assertTrue(binding.contains("Config.maxLinkedStorages()"));
        assertFalse(binding.contains("MAX_LINKED_STORAGES"));
    }
}
