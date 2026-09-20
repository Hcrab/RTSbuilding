package com.rtsbuilding.rtsbuilding.client.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定储存状态边界，避免分页、合成和绑定职责重新合并。 */
class StorageOwnerContractTest {
    private static final Path ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/controller");

    @Test
    void storageOwnersRemainSmallAndExplicit() throws Exception {
        assertLineLimit("StorageBindingState.java", 250);
        assertLineLimit("StorageCraftState.java", 250);
        assertLineLimit("StoragePagePayloadDecoder.java", 220);
        assertLineLimit("StorageRefreshState.java", 180);
        assertLineLimit("StorageUiValueSanitizer.java", 100);
        assertLineLimit("StorageStateManager.java", 820);

        String manager = source("StorageStateManager.java");
        assertTrue(manager.contains("StorageBindingState bindingState"));
        assertTrue(manager.contains("StorageCraftState craftState"));
        assertTrue(manager.contains("StorageRefreshState refreshState"));
        assertTrue(manager.contains("StoragePagePayloadDecoder.DecodedPage"));
        assertTrue(manager.contains("StorageUiValueSanitizer.normalizeCategory"));
    }

    private static String source(String name) throws Exception {
        return Files.readString(ROOT.resolve(name), StandardCharsets.UTF_8);
    }

    private static void assertLineLimit(String name, int max) throws Exception {
        try (var stream = Files.lines(ROOT.resolve(name), StandardCharsets.UTF_8)) {
            assertTrue(stream.count() <= max, name + " 超过储存 owner 门禁");
        }
    }
}
