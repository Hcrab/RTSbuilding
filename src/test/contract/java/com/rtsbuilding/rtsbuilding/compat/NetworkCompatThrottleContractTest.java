package com.rtsbuilding.rtsbuilding.compat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkCompatThrottleContractTest {
    @Test
    void ae2AndRefinedStorageUseSharedConfigurableRefreshGate() throws Exception {
        String config = read("src/main/java/com/rtsbuilding/rtsbuilding/Config.java");
        String ae2 = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/ae2/RtsAe2Compat.java");
        String rs = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/refinedstorage/RtsRefinedStorageCompat.java");
        String linked = read("src/main/java/com/rtsbuilding/rtsbuilding/server/storage/handler/RtsLinkedCapabilities.java");

        assertTrue(config.contains("java.util.List.of(\"storage\", \"ae2NetworkRefreshThrottle\")"));
        assertTrue(config.contains("java.util.List.of(\"storage\", \"refinedStorageNetworkRefreshThrottle\")"));
        assertTrue(ae2.contains("implements IItemHandler") && ae2.contains("RefreshableSnapshotHandler"));
        assertTrue(ae2.contains("Config.ae2NetworkRefreshThrottle()"));
        assertTrue(rs.contains("RefreshableSnapshotHandler"));
        assertTrue(rs.contains("Config.refinedStorageNetworkRefreshThrottle()"));
        assertTrue(linked.contains(
                "RtsRefinedStorageCompat.createNetworkItemHandler(player, level, pos)"),
                "跨维度链接必须把已解析的精确 ServerLevel 交给 RS，不能退回玩家当前维度");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}
