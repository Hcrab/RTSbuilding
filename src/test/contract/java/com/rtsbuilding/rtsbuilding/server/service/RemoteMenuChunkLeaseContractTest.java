package com.rtsbuilding.rtsbuilding.server.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 远程菜单必须先准备完整区块，并把强加载限制在每名玩家当前使用的一个目标上。 */
class RemoteMenuChunkLeaseContractTest {
    @Test
    void 远距离菜单使用单区块票据并同步完整Chunk() throws Exception {
        String lease = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsRemoteMenuChunkLease.java"));

        assertTrue(lease.contains("Map<UUID, Lease> LEASES"));
        assertTrue(lease.contains("requestPlayerTicket("));
        assertTrue(lease.contains("ticket.setChunkListDepth(1)"));
        assertTrue(lease.contains("ForgeChunkManager.forceChunk(ticket, chunkPos)"));
        assertTrue(lease.contains("new SPacketChunkData(chunk, FULL_CHUNK_SECTION_MASK)"));
        assertTrue(lease.contains("ForgeChunkManager.unforceChunk(ticket, chunkPos)"));
        assertTrue(lease.contains("ForgeChunkManager.releaseTicket(ticket)"));
    }

    @Test
    void 生产交互先准备区块再判定加载状态并在未开窗时释放() throws Exception {
        String interaction = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsInteractionServiceImpl.java"));
        int prepare = interaction.indexOf("RtsRemoteMenuService.prepareTargetChunk(player, clickedPos, traceId)");
        int access = interaction.indexOf("RtsLinkedStorageResolver.canAccessWorldTarget(player, clickedPos)");

        assertTrue(prepare >= 0 && access > prepare,
                "远程目标必须在 isBlockLoaded 判定之前完成强加载");
        assertTrue(interaction.contains("if (preparedRemoteChunk)"));
        assertTrue(interaction.contains("RtsRemoteMenuService.releasePreparedTarget(player, traceId"));

        String menuService = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsRemoteMenuService.java"));
        int clear = menuService.indexOf("public static void clearValidation(");
        assertTrue(clear >= 0 && menuService.indexOf("RtsRemoteMenuChunkLease.release(player, traceId", clear) > clear,
                "关闭菜单、退出 RTS 或会话失效时必须释放区块票据");
    }
}
