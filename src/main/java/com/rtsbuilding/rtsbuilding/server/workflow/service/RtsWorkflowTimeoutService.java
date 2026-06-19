package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定期扫描所有玩家的工作流槽位，并移除超过可配置阈值空闲时间的条目。
 *
 * <p>防止「僵尸」工作流——那些被挂起或被断开连接的玩家遗留的条目——
 * 永久占用槽位。服务为选配性质；在引擎初始化后调用 {@link #start(Duration, Duration)}。</p>
 *
 * <p>使用单个守护后台线程进行扫描定时器。实际的清理逻辑通过引擎在服务端 tick 线程上运行。</p>
 */
public final class RtsWorkflowTimeoutService {

    private final Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers;
    private final Map<UUID, ServerPlayer> playerRefs;
    private final RtsWorkflowEventBus eventBus;
    private final RtsWorkflowSyncService syncService;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    /**
     * @param slotManagers 引擎的 slot 管理器映射
     * @param playerRefs   引擎的玩家引用缓存
     * @param eventBus     工作流事件总线
     * @param syncService  网络同步服务
     */
    public RtsWorkflowTimeoutService(
            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers,
            Map<UUID, ServerPlayer> playerRefs,
            RtsWorkflowEventBus eventBus,
            RtsWorkflowSyncService syncService) {
        this.slotManagers = slotManagers;
        this.playerRefs = playerRefs;
        this.eventBus = eventBus;
        this.syncService = syncService;
    }

    /**
     * 启动定期超时扫描。
     *
     * @param checkInterval 扫描过期工作流的间隔
     * @param maxIdleTime   没有任何进度更新的最大允许时间
     */
    public void start(Duration checkInterval, Duration maxIdleTime) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // 已在运行
        }
        long intervalMs = checkInterval.toMillis();
        long maxIdleMs = maxIdleTime.toMillis();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RTS-Workflow-Timeout");
            t.setDaemon(true);
            return t;
        });

        task = scheduler.scheduleWithFixedDelay(
                () -> scanAndCleanup(maxIdleMs),
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        RtsbuildingMod.LOGGER.info("[WorkflowTimeout] Started (interval={}, maxIdle={})",
                checkInterval, maxIdleTime);
    }

    /**
     * 停止定期扫描。幂等操作。
     */
    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /**
     * 执行单次清理并触发 TIMEOUT 事件。
     *
     * <p>{@code slotManagers} 是一个 {@link ConcurrentHashMap}，
     * 其 {@code keySet().toArray()} 无需外部同步即可提供安全快照。
     * 清理内部遍历所有槽位管理器，并为过时条目触发 TIMEOUT 事件。</p>
     */
    private void scanAndCleanup(long maxIdleMs) {
        int total = 0;

        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry : slotManagers.entrySet()) {
            UUID playerId = playerEntry.getKey();

            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : playerEntry.getValue().entrySet()) {
                RtsWorkflowSlotManager slots = dimEntry.getValue();

                List<Integer> staleIds = slots.removeStaleEntries(maxIdleMs);
                for (int staleId : staleIds) {
                    eventBus.fire(new WorkflowEvent(WorkflowEventType.TIMEOUT, playerId, staleId, null));
                    total++;
                }

                if (!staleIds.isEmpty()) {
                    ServerPlayer player = findPlayerByUUID(playerId);
                    if (player != null) {
                        if (slots.occupiedCount() > 0) {
                            syncService.notifyPlayer(player, slots);
                        } else {
                            syncService.sendIdle(player);
                        }
                    }
                }
            }

            // 移除空的维度映射
            playerEntry.getValue().entrySet().removeIf(e -> e.getValue().occupiedCount() == 0 && e.getValue().size() == 0);
        }

        // 移除没有任何维度的玩家
        slotManagers.values().removeIf(Map::isEmpty);

        if (total > 0) {
            RtsbuildingMod.LOGGER.info("[WorkflowTimeout] Cleaned up {} stale workflow(s)", total);
        }
    }

    @Nullable
    private ServerPlayer findPlayerByUUID(UUID playerId) {
        ServerPlayer cached = playerRefs.get(playerId);
        if (cached != null && cached.level() != null && !cached.level().isClientSide()) {
            return cached;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                playerRefs.put(playerId, online);
                return online;
            }
        }
        playerRefs.remove(playerId);
        return null;
    }
}
