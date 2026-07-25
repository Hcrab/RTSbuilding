package com.rtsbuilding.rtsbuilding.server.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定插件状态、队伍 SavedData 与玩家背包必须一起进入耐久化检查点。
 */
class RtsPluginDurabilityContractTest {

    @Test
    void pluginMutationsPersistStateAndInventoryBeforeReturning() throws Exception {
        String durability = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginDurability.java"));
        String teamService = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginTeamService.java"));

        assertTrue(teamService.contains("RtsPluginDurability.checkpoint(player);"));
        assertTrue(durability.contains("storageLevel.getDataStorage().save();"));
        assertTrue(durability.contains("server.getPlayerList().saveAll();"));
    }
}
