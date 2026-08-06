package com.rtsbuilding.rtsbuilding.server.storage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 确保解绑与优先级操作不会把跨维目标退化为玩家当前维度。 */
class CrossDimensionStorageIdentityContractTest {

    @Test
    void linkedStorageActionsCarryAndUseExactDimensionIdentity() throws Exception {
        String unlinkPayload = read("src/main/java/com/rtsbuilding/rtsbuilding/network/storage/C2SRtsUnlinkStoragePayload.java");
        String updatePayload = read("src/main/java/com/rtsbuilding/rtsbuilding/network/storage/C2SRtsUpdateLinkedStoragePayload.java");
        String handler = read("src/main/java/com/rtsbuilding/rtsbuilding/network/storage/handler/RtsBindingHandlers.java");
        String binding = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsBindingServiceImpl.java");
        String ui = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/storage/StorageUiAdapter.java");

        assertTrue(unlinkPayload.contains("ResourceLocation dimension, BlockPos pos"));
        assertTrue(updatePayload.contains("ResourceLocation dimension, BlockPos pos"));
        assertTrue(handler.contains("RtsDimensionKeys.create(payload.dimension())"));
        assertTrue(binding.contains("unlinkStorage(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos)"));
        assertTrue(binding.contains("updateLinkedStorageSettings(ServerPlayer player, ResourceKey<Level> dimension"));
        assertTrue(ui.contains("dimension+\"|\"+p.getX()"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
