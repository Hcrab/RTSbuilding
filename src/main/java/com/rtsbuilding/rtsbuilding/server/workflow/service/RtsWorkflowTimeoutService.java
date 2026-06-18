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
 * Periodically scans all players' workflow slots and removes entries
 * that have been idle beyond a configurable threshold.
 *
 * <p>This prevents "zombie" workflows — entries that were suspended or
 * left behind by disconnected players — from permanently occupying slots.
 * The service is opt-in; call {@link #start(Duration, Duration)} after
 * the engine is initialised.</p>
 *
 * <p>Uses a single daemon background thread for the scan timer.  The
 * actual cleanup logic runs on the server tick thread via the engine.</p>
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
     * Starts the periodic timeout scan.
     *
     * @param checkInterval how often to scan for stale workflows
     * @param maxIdleTime   maximum allowed time without any progress update
     */
    public void start(Duration checkInterval, Duration maxIdleTime) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // already running
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
     * Stops the periodic scan.  Idempotent.
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
     * Performs a single cleanup pass and fires TIMEOUT events.
     *
     * <p>{@code slotManagers} is a {@link ConcurrentHashMap} whose
     * {@code keySet().toArray()} already provides a safe snapshot without
     * external synchronization.  The cleanup iterates all slot managers
     * internally and fires TIMEOUT events for stale entries.</p>
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

            // Remove empty dimension maps
            playerEntry.getValue().entrySet().removeIf(e -> e.getValue().occupiedCount() == 0 && e.getValue().size() == 0);
        }

        // Remove players with no dimensions
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
