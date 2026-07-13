package com.rtsbuilding.rtsbuilding.blueprint.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintPlacementLifecycleTest {
    @Test
    void forgePlayerTickConsumesQueuedBlueprintPlacementJobs() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java"));

        assertTrue(source.contains("BlueprintPlacementService.tick(serverPlayer)"),
                "蓝图放置队列必须在 Forge 玩家 tick 中消费；否则 Build 只会入队，不会真正落块。");
    }
}
