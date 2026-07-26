package com.rtsbuilding.rtsbuilding.server.service.page;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保证 Forge 1.20.1 的储存分页配置不是只有翻译文本，而是实际接入页面链路。
 */
class StoragePageConfigContractTest {

    @Test
    void storagePageLimitsAndLruCapacityAreConfigDriven() throws Exception {
        String config = read("src/main/java/com/rtsbuilding/rtsbuilding/Config.java");
        String helpers = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/page/RtsPageSharedHelpers.java");
        String core = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/page/RtsPageCore.java");
        String cache = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/page/RtsPageCache.java");
        String browser = read("src/main/java/com/rtsbuilding/rtsbuilding/server/storage/session/RtsBrowserState.java");

        assertTrue(config.contains("java.util.List.of(\"storage\", \"pageCacheMaxPlayers\"), 256, 1, 4096"));
        assertTrue(config.contains("java.util.List.of(\"storage\", \"defaultStoragePageSize\"), 90, 1, 4096"));
        assertTrue(config.contains("java.util.List.of(\"storage\", \"maxStoragePageSize\"), 180, 1, 8192"));
        assertTrue(helpers.contains("Config.defaultStoragePageSize()"));
        assertTrue(helpers.contains("Config.maxStoragePageSize()"));
        assertTrue(core.contains("RtsPageCache.INSTANCE.get(player.getUUID())"));
        assertTrue(core.contains("RtsPageCache.INSTANCE.put(player.getUUID()"));
        assertTrue(cache.contains("new LinkedHashMap<>(16, 0.75F, true)"));
        assertTrue(cache.contains("Config.pageCacheMaxPlayers()"));
        assertTrue(browser.contains("RtsStoragePageBuilder.defaultPageSize()"));
        assertFalse(helpers.contains("MAX_PAGE_SIZE = 180"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
