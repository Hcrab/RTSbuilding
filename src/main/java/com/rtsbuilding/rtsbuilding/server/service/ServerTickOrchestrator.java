package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
 *   <li>{@link #warmCreativeTabCaches(MinecraftServer)} — 预热创造模式标签页缓存：
 *       清除缓存状态后，以正常模式和搜索模式各预热一次</li>
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
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
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
        RtsPlacementBatch.tickPlaceBatchJobs(player, session);
        RtsDestructionBatch.tickDestroyJobs(player, session);
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
                ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
                // 存储变化后自动尝试恢复挂起放置作业
                RtsPendingPlacementService.tryResumeAfterStorageChange(player);
                // 存储变化后也尝试恢复挂起的破坏作业（新工具可能已存入存储）
                RtsDestructionBatch.tryResumePendingDestroyJobs(player, session);
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
