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

        int ae2Identity = batch.indexOf("RtsAe2Compat.batchNetworkIdentity");
        int rsIdentity = batch.indexOf("RtsRefinedStorageCompat.batchNetworkIdentity");
        int link = batch.indexOf("ensureStorageLinked(");
        assertTrue(batch.contains("new IdentityHashMap<>()"));
        assertTrue(batch.contains("seedExistingNetworkIdentities"));
        assertTrue(ae2Identity >= 0 && rsIdentity >= 0 && link > ae2Identity && link > rsIdentity,
                "必须先按第三方网络对象身份去重，再创建正式链接 handler");
        assertFalse(batch.contains("RtsLinkedCapabilities.findLinkedItemHandler"),
                "批量发现阶段不能为每个网络节点创建完整库存快照");
    }

    @Test
    void linkedStorageLimitComesFromServerConfig() throws IOException {
        String config = Files.readString(ROOT.resolve(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String binding = Files.readString(ROOT.resolve(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/bindings/RtsLinkedStorageBindingService.java"));

        assertTrue(config.contains(
                "java.util.List.of(\"storage\", \"maxLinkedStorages\"), 200, 1, 4096"));
        assertTrue(binding.contains("Config.maxLinkedStorages()"));
        assertFalse(binding.contains("MAX_LINKED_STORAGES"));
    }
}
