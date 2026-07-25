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
        String rs = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/rs/RtsRsCompat.java");

        assertTrue(config.contains("storage.ae2NetworkRefreshThrottle"));
        assertTrue(config.contains("storage.refinedStorageNetworkRefreshThrottle"));
        assertTrue(ae2.contains("implements IItemHandler") && ae2.contains("RefreshableSnapshotHandler"));
        assertTrue(ae2.contains("Config.ae2NetworkRefreshThrottle()"));
        assertTrue(rs.contains("RefreshableSnapshotHandler"));
        assertTrue(rs.contains("Config.refinedStorageNetworkRefreshThrottle()"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}
