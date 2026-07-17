package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsStoragePageRequestCoalescer;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 服务端主线程的 Tick 编排入口。所有长任务先共享 Task Engine 预算，再统一提交副作用。 */
public final class ServerTickOrchestrator {
    private static final ServerTickOrchestrator INSTANCE = new ServerTickOrchestrator();

    private ServerTickOrchestrator() {
    }

    public static ServerTickOrchestrator getInstance() {
        return INSTANCE;
    }

    public void onPlayerTickPost(ServerPlayer player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) return;
        if (session.transfer.remoteMenuContainerId < 0
                && !RtsRemoteMenuCompat.isSupportedRemoteMenu(player.containerMenu)) {
            RtsRemoteMenuService.clearValidation(player, session);
        }
        if (session.transfer.remoteMenuContainerId >= 0
                && (player.containerMenu == null
                || player.containerMenu.containerId != session.transfer.remoteMenuContainerId)) {
            RtsRemoteMenuService.clearValidation(player, session);
        }
    }

    public void tickMining(MinecraftServer server) {
        var sessionService = ServiceRegistry.getInstance().session();
        var changes = RtsStorageTickService.INSTANCE.tick();
        for (var entry : changes.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            RtsStorageSession session = sessionService.getIfPresent(player);
            if (session == null) continue;
            session.transfer.pageDataVersion.incrementAndGet();
            if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) continue;
            RtsEffectAccumulator.INSTANCE.markStorageViewDirty(
                    player.getUUID(), player.level().dimension());
            RtsPendingPlacementService.tryResumeAfterStorageChange(player, entry.getValue());
        }

        // 放置、拆除、挖掘、缓冲写回、蓝图、漏斗和已放置回收共用同一预算。
        var taskStats = RtsTaskEngine.INSTANCE.tick(server);
        RtsDeveloperMetrics.recordTaskTick(server, taskStats);

        RtsWorkflowEngine.getInstance().tickTimeoutService(
                server, server.overworld().getGameTime());
        RtsStoragePageRequestCoalescer.flushPending();
        var effectReport = RtsEffectAccumulator.INSTANCE.flush(server);
        RtsDeveloperMetrics.recordEffectCommit(effectReport);
    }
}
