package com.rtsbuilding.rtsbuilding.server.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RtsRemoteMenuChunkSyncContractTest {
    @Test
    void fullChunkSnapshotPrecedesRemoteMenuHintAndHasSafeFallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsRemoteMenuService.java"));

        int sync = source.indexOf("syncRemoteTargetChunk(player, level, pos)");
        int hint = source.indexOf("new S2CRtsRemoteMenuHintPayload(pos)", sync);
        assertTrue(sync >= 0 && hint > sync,
                "远程目标区块必须先于开窗提示同步，第三方客户端菜单才能读取方块实体。");
        assertTrue(source.contains("ClientboundLevelChunkWithLightPacket"));
        assertTrue(source.contains("ClientboundChunkBatchStartPacket.INSTANCE"));
        assertTrue(source.contains("new ClientboundChunkBatchFinishedPacket(1)"));
        assertTrue(source.contains("player.getChunkTrackingView().contains"),
                "已追踪区块不得被完整快照替换，否则第三方客户端多方块关联会丢失。");
        assertTrue(source.contains("catch (RuntimeException exception)"));
        assertTrue(source.contains("falling back to block updates"));
    }
}
