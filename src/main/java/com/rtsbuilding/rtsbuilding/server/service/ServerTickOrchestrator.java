package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务器全局 Tick 编排器——管理所有非玩家生命周期 tick 逻辑。
 *
 * <p>Phase 3 从 {@link RtsSessionService} 独立出来的 tick 调度中心，
 * 负责处理存储缓存刷新、每玩家 tick（漏斗/挖掘/放置恢复）、
 * Pipeline 实例 tick 和创造模式标签页缓存预热。
 */
public final class ServerTickOrchestrator {

    private static final ServerTickOrchestrator INSTANCE = new ServerTickOrchestrator();

    private ServerTickOrchestrator() {
    }

    public static ServerTickOrchestrator getInstance() {
        return INSTANCE;
    }

    // ======================================================================
    //  生命周期 Tick
    // ======================================================================

    /**
     * 玩家 Post-Tick——处理远程菜单验证和批量放置 tick。
     */
    public void onPlayerTickPost(ServerPlayer player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) {
            return;
        }
        if (session.transfer.remoteMenuContainerId < 0
                && !RtsRemoteMenuCompat.isSupportedRemoteMenu(player.containerMenu)) {
            RtsMenuRemoteService.clearValidation(player, session);
        }
        if (session.transfer.remoteMenuContainerId >= 0
                && (player.containerMenu == null || player.containerMenu.containerId != session.transfer.remoteMenuContainerId)) {
            RtsMenuRemoteService.clearValidation(player, session);
        }
        RtsPlacementBatch.tickPlaceBatchJobs(player, session);
    }

    // ======================================================================
    //  服务器全局 Tick
    // ======================================================================

    /**
     * 全局 tick——存储缓存刷新 + 每玩家 tick（挖掘、漏斗、放置恢复）+ Pipeline tick。
     */
    public void tickMining(MinecraftServer server) {
        // Tick storage cache refresh (every N ticks per player)
        var changes = RtsStorageTickService.INSTANCE.tick();

        // When cache detects item changes, push updated page to the client
        if (!changes.isEmpty()) {
            for (var entry : changes.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
                if (session == null) continue;
                // Increment data version so the page cache in RtsPageCore
                // knows the storage data has changed and should rebuild.
                session.transfer.pageDataVersion.incrementAndGet();
                if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) continue;
                ServiceRegistry.getInstance().page().requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
                // 存储变化后自动尝试恢复挂起放置作业
                RtsPendingPlacementService.tryResumeAfterStorageChange(player);
            }
        }

        for (var entry : ServiceRegistry.getInstance().session().allSessions().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            RtsMiningStateMachine.tickActiveMining(player, session);
            ServiceRegistry.getInstance().funnel().tick(player, session);
            RtsPlacedRecoveryService.tick(player, session);
        }

        // Tick all active tickable pipeline instances (ultimine/area-mine monitoring)
        TickablePipelineRegistry.tickAll();
    }

    // ======================================================================
    //  缓存预热
    // ======================================================================

    /**
     * 预热创造模式标签页缓存。
     */
    public void warmCreativeTabCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (ServerTickOrchestrator.class) {
            RtsStoragePageBuilder.clearCreativeTabCacheState();
            ServerLevel level = server.overworld();
            if (level == null) {
                return;
            }
            RtsStoragePageBuilder.warmCreativeTabCacheMode(level, false);
            RtsStoragePageBuilder.warmCreativeTabCacheMode(level, true);
        }
    }
}
