package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsStoragePageRequestCoalescer;
import com.rtsbuilding.rtsbuilding.server.service.api.SessionService;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskScheduler;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectCommitBarrier;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 服务端主线程的 Tick 编排入口。所有长任务先共享 Task Engine 预算，再统一提交副作用。 */
public final class ServerTickOrchestrator {
    private static final ServerTickOrchestrator INSTANCE = new ServerTickOrchestrator();

    private ServerTickOrchestrator() {
    }

    public static ServerTickOrchestrator getInstance() {
        return INSTANCE;
    }

    public void onPlayerTickPost(EntityPlayerMP player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) return;
        if (session.transfer.remoteMenuContainerId < 0
                && !RtsRemoteMenuCompat.isSupportedRemoteMenu(player.openContainer)) {
            RtsRemoteMenuService.clearValidation(player, session, "NO_REMOTE_WINDOW");
        }
        if (session.transfer.remoteMenuContainerId >= 0
                && (player.openContainer == null
                || player.openContainer.windowId != session.transfer.remoteMenuContainerId)) {
            int actualWindow = player.openContainer == null ? -1 : player.openContainer.windowId;
            RtsRemoteMenuService.clearValidation(player, session,
                    "WINDOW_MISMATCH expected=" + session.transfer.remoteMenuContainerId
                            + " actual=" + actualWindow);
        }
    }

    public void tickMining(MinecraftServer server) {
        SessionService sessionService = ServiceRegistry.getInstance().session();
        Map<UUID, Set<String>> changes = RtsStorageTickService.INSTANCE.tick();
        for (Map.Entry<UUID, Set<String>> entry : changes.entrySet()) {
            EntityPlayerMP player = com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getPlayerList(server).getPlayerByUUID(entry.getKey());
            if (player == null) continue;
            RtsStorageSession session = sessionService.getIfPresent(player);
            if (session == null) continue;
            session.transfer.pageDataVersion.incrementAndGet();
            if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) continue;
            RtsEffectAccumulator.INSTANCE.markStorageViewDirty(
                    player.getUniqueID(), player.dimension);
            RtsPendingPlacementService.tryResumeAfterStorageChange(player, entry.getValue());
        }

        // 放置、拆除、挖掘、缓冲写回、蓝图、漏斗和已放置回收共用同一预算。
        TaskScheduler.TickStats taskStats = RtsTaskEngine.INSTANCE.tick(server);
        RtsDeveloperMetrics.recordTaskTick(server, taskStats);

        WorldServer overworld = com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getWorld(server, 0);
        RtsWorkflowEngine.getInstance().tickTimeoutService(
                server, overworld == null ? 0L : overworld.getTotalWorldTime());
        RtsStoragePageRequestCoalescer.flushPending();
        RtsEffectCommitBarrier.CommitReport effectReport = RtsEffectAccumulator.INSTANCE.flush(server);
        RtsDeveloperMetrics.recordEffectCommit(effectReport);
    }
}
