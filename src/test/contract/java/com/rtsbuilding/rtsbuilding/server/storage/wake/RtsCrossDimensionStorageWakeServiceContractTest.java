package com.rtsbuilding.rtsbuilding.server.storage.wake;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 约束跨维存储的 ticket 必须随租约持有，而不是在本次解析完成后立刻释放。
 * This source contract keeps the ticket lifecycle independent of a running Forge server.
 */
class RtsCrossDimensionStorageWakeServiceContractTest {
    @Test
    void ticketIsRetainedPerEndpointThenReleasedWhenTheLeaseExpires() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/wake/"
                        + "RtsCrossDimensionStorageWakeService.java"));
        int ensureReady = service.indexOf("public synchronized boolean ensureReady(");
        int tick = service.indexOf("public synchronized void tick(");
        String ensureReadyBody = service.substring(ensureReady, tick);
        int sameDimension = ensureReadyBody.indexOf("if (player.dimension == targetLevel.provider.getDimension())");
        int crossDimensionConfig = ensureReadyBody.indexOf("if (!Config.isCrossDimensionStorageEnabled()");

        assertTrue(sameDimension >= 0 && crossDimensionConfig > sameDimension);
        assertTrue(ensureReadyBody.substring(sameDimension, crossDimensionConfig)
                .contains("return targetLevel.isBlockLoaded(pos)"),
                "同维访问必须保留原有路径，不被跨维 ticket、距离或冷却规则拦截");
        assertTrue(service.contains("ticketsByPlayer"));
        assertTrue(ensureReadyBody.contains("TicketBinding existing = ticketFor("));
        assertTrue(ensureReadyBody.contains("ForgeChunkManager.forceChunk(ticket, chunkPos)"));
        assertTrue(ensureReadyBody.contains("bindTicket(player.getUniqueID(), endpoint"));
        assertFalse(ensureReadyBody.contains("ForgeChunkManager.unforceChunk(ticket, chunkPos)"),
                "ensureReady 完成后不得立即 unforce ticket");
        assertFalse(ensureReadyBody.contains("ForgeChunkManager.releaseTicket(ticket)"),
                "ensureReady 完成后不得立即 release ticket");
        assertTrue(service.contains("releaseExpiredTickets(overworld.getTotalWorldTime())"));
        assertTrue(service.contains("releaseTicket(lease.playerId(), lease.endpoint())"));
        assertTrue(service.contains("ForgeChunkManager.unforceChunk(ticket, chunkPos)"));
        assertTrue(service.contains("ForgeChunkManager.releaseTicket(ticket)"));
    }

    @Test
    void capacityAndTicketFailuresOnlyFallBackToAlreadyLoadedTargets() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/wake/"
                        + "RtsCrossDimensionStorageWakeService.java"));

        assertTrue(service.contains("result == RtsCrossDimensionWakeLeaseTable.TouchResult.CAPACITY_REACHED"));
        assertTrue(service.contains("if (ticket == null)"));
        assertTrue(service.contains("leases.release(player.getUniqueID(), endpoint)"));
        assertTrue(service.contains("return targetLevel.isBlockLoaded(pos)"));
    }

    @Test
    void logoutAndServerStopReachTheTicketReleasePaths() throws Exception {
        String mod = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java"));
        int logout = mod.indexOf("public void onPlayerLogout(");
        int serverTick = mod.indexOf("public void onServerTick(");
        int stopping = mod.indexOf("public void onServerStopping(");
        int stopped = mod.indexOf("public void onServerStopped(");

        assertTrue(logout >= 0 && serverTick > logout && stopping >= 0 && stopped > stopping);
        assertTrue(mod.indexOf(
                "RtsCrossDimensionStorageWakeService.INSTANCE.releasePlayer(player.getUniqueID())", logout)
                > logout);
        assertTrue(mod.indexOf("RtsCrossDimensionStorageWakeService.INSTANCE.tick(server)", serverTick)
                > serverTick);
        assertTrue(mod.indexOf("RtsCrossDimensionStorageWakeService.INSTANCE.clear()", stopping) > stopping,
                "停服前必须在 WorldServer 仍可用时释放 ticket");
        assertTrue(mod.indexOf("RtsCrossDimensionStorageWakeService.INSTANCE.clear()", stopped) > stopped,
                "停服后保留幂等清理，处理任何异常残留状态");
    }
}
