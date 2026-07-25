package com.rtsbuilding.rtsbuilding.compat.create;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保证蓝图生产链完整经过 Create 的更新标志、NBT 准备与放置回调。
 */
class BlueprintCreatePlacementCompatContractTest {

    @Test
    void blueprintPlacementUsesCreateLifecycleAtEveryRequiredStage() throws Exception {
        String compat = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/create/BlueprintCreatePlacementCompat.java");
        String placement = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/blueprint/server/BlueprintPlacementService.java");

        assertTrue(compat.contains("prepareBlockEntityData")
                        && compat.contains("BlockState.class")
                        && compat.contains("fallbackSanitize")
                        && compat.contains("\"Controller\"")
                        && compat.contains("\"LastKnownPos\""),
                "Create 插头必须兼容 1.20.1 写出器并清理旧世界拓扑");
        assertTrue(placement.contains("BlueprintCreatePlacementCompat.placementFlags(state)")
                        && placement.contains("BlueprintCreatePlacementCompat.prepareBlockEntityTag")
                        && placement.contains("BlueprintCreatePlacementCompat.finishPlacement"),
                "蓝图生产链必须接入更新标志、NBT 准备和放置完成回调");
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
