package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsStoragePageRequestCoalescer;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;

/**
 * 服务器全局 Tick 编排器——管理所有非玩家生命周期的 tick 循环逻辑。
 *
 * <p>Phase 3 重构中从 {@code RtsSessionService} 独立出来的全局 tick 调度中心。
 * 单例模式，通过 {@link #getInstance()} 获取。
 *
 * <p><b>核心调度方法：</b>
 * <ul>
 *   <li>{@link #onPlayerTickPost(ServerPlayer)} — 玩家后 tick 处理：
 *       <ul>
 *         <li>远程菜单验证——如果远程菜单容器 ID 不匹配或已关闭则清除验证状态</li>
 *         <li>批量放置 tick — {@link RtsPlacementBatch#tickPlaceBatchJobs}</li>
 *       </ul>
 *   </li>
 *   <li>{@link #tickMining(MinecraftServer)} — 服务器全局 tick：
 *       <ul>
 *         <li>存储缓存刷新 — {@link RtsStorageTickService#tick()}，
 *             检测到变更时递增数据版本、推送刷新页面、尝试恢复挂起作业</li>
 *         <li>每玩家 tick — 遍历所有会话：挖掘状态机 tick、漏斗 tick、放置恢复 tick</li>
 *         <li>Pipeline 实例 tick — {@link TickablePipelineRegistry#tickAll()}</li>
 *       </ul>
 *   </li>
 * </ul>
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
        var registry = ServiceRegistry.getInstance();
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) {
            return;
        }
        if (session.transfer.remoteMenuContainerId < 0
                && !RtsRemoteMenuCompat.isSupportedRemoteMenu(player.containerMenu)) {
            RtsRemoteMenuService.clearValidation(player, session);
        }
        if (session.transfer.remoteMenuContainerId >= 0
                && (player.containerMenu == null || player.containerMenu.containerId != session.transfer.remoteMenuContainerId)) {
            RtsRemoteMenuService.clearValidation(player, session);
        }
        // 放置、拆除与挖掘统一在服务器 Post-Tick 的全局预算中推进。
    }

    // ======================================================================
    //  服务器全局 Tick
    // ======================================================================

    /**
     * 全局 tick——存储缓存刷新 + 每玩家 tick（挖掘、漏斗、放置恢复）+ Pipeline tick。
     */
    public void tickMining(MinecraftServer server) {
        var registry = ServiceRegistry.getInstance();
        var sessionService = registry.session();
        var funnelService = registry.funnel();

        // Tick storage cache refresh (every N ticks per player)
        var changes = RtsStorageTickService.INSTANCE.tick();

        // When cache detects item changes, push updated page to the client
        if (!changes.isEmpty()) {
            for (var entry : changes.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                RtsStorageSession session = sessionService.getIfPresent(player);
                if (session == null) continue;
                // Increment data version so the page cache in RtsPageCore
                // knows the storage data has changed and should rebuild.
                session.transfer.pageDataVersion.incrementAndGet();
                if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) continue;
                RtsEffectAccumulator.INSTANCE.markStorageViewDirty(
                        player.getUUID(), player.level().dimension());
                // 存储变化后自动尝试恢复挂起放置作业
                RtsPendingPlacementService.tryResumeAfterStorageChange(player, entry.getValue());
                // 拆除恢复由下方 TaskEngine 的同步阶段统一处理，避免同一 Tick 重复借还工具。
            }
        }

        // 先在一个全服务器预算内公平推进放置、拆除与挖掘。
        var taskStats = RtsTaskEngine.INSTANCE.tick(server);
        RtsDeveloperMetrics.recordTaskTick(server, taskStats);

        // 漏斗和恢复服务尚未迁入按 unit 计量的 Executor；保留明确兼容边界。
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RtsStorageSession session = sessionService.getIfPresent(player);
            if (session == null) continue;
            funnelService.tick(player, session);
            RtsPlacedRecoveryService.tick(player, session);
            // 智能放置批量流体放置 tick
            com.rtsbuilding.rtsbuilding.server.service.fluids.SmartPlaceFluidBatch.tickFluidBatchJobs(player, session);
        }

        // Tick all active tickable pipeline instances (ultimine/area-mine monitoring)
        TickablePipelineRegistry.tickAll();

        // 页面请求在所有本 tick 储存变更之后合并执行；每位玩家最多构建最后请求的一页。
        RtsStoragePageRequestCoalescer.flushPending();

        // 所有任务推进完成后再统一发送页面失效通知与工作流快照。
        RtsEffectAccumulator.INSTANCE.flush(server);
    }

    // ======================================================================
    //  缓存预热
    // ======================================================================

}
