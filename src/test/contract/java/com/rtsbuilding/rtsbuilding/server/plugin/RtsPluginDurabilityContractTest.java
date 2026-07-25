package com.rtsbuilding.rtsbuilding.server.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPluginDurabilityContractTest {
    @Test
    void pluginMutationPersistsPluginStateOnly() throws IOException {
        String durability = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginDurability.java"));
        String teamService = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginTeamService.java"));

        assertTrue(teamService.contains("RtsPluginDurability.checkpoint(player);"),
                "个人与队伍插件保存入口都必须经过即时耐久化检查点。");
        assertTrue(durability.contains("SaveScheduler.INSTANCE.player(player).flush()"),
                "个人插件状态必须立即写入 DataCluster（session.dat）。");
        assertTrue(durability.contains("storageLevel.getDataStorage().save()"),
                "队伍共享插件必须立即提交 SavedData 保存。");
        assertTrue(durability.contains("IOUtilities.waitUntilIOWorkerComplete()"),
                "队伍 SavedData 的异步写入必须在操作返回前完成。");
        assertTrue(!durability.contains("server.getPlayerList().saveAll()"),
                "背包变更由 Minecraft 自然保存周期持久化，不由 checkpoint 主动触发，"
                + "避免双存储系统在崩溃时状态不一致。");
    }
}
