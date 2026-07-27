package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
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
 * Periodically scans all players' workflow slots and removes entries that have exceeded a configurable idle time threshold.
 *
 * <p>Prevents "zombie" workflows — entries left behind by suspended or disconnected players —
 * from permanently occupying slots. The service is optional; call {@link #start(Duration, Duration)} after engine initialization.</p>
 *
 * <p>Uses a single daemon background thread for the scan timer. Actual cleanup logic runs through the engine on the server tick thread.</p>
 */
public final class RtsWorkflowTimeoutService {

    private final Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers;
    private final Map<UUID, ServerPlayer> playerRefs;
    private final RtsWorkflowSyncService syncService;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    /**
     * @param slotManagers Engine's slot manager map
     * @param playerRefs   Engine's player reference cache
     * @param syncService  Network sync service
     */
    public RtsWorkflowTimeoutService(
            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers,
            Map<UUID, ServerPlayer> playerRefs,
            RtsWorkflowSyncService syncService) {
        this.slotManagers = slotManagers;
        this.playerRefs = playerRefs;
        this.syncService = syncService;
    }

    /**
     * Start periodic timeout scanning.
     *
     * @param checkInterval Interval for scanning expired workflows
     * @param maxIdleTime   Maximum allowed time without any progress updates
     */
    public void start(Duration checkInterval, Duration maxIdleTime) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // Already running
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
     * Stop periodic scanning. Idempotent operation.
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
     * Execute a single cleanup pass and fire TIMEOUT events.
     *
     * <p>{@code slotManagers} is a {@link ConcurrentHashMap} whose
     * {@code keySet().toArray()} provides a safe snapshot without external synchronization.
     * The cleanup traverses all slot managers and fires TIMEOUT events for stale entries.</p>
     */
    private void scanAndCleanup(long maxIdleMs) {
        int total = 0;

        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry : slotManagers.entrySet()) {
            UUID playerId = playerEntry.getKey();

            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : playerEntry.getValue().entrySet()) {
                RtsWorkflowSlotManager slots = dimEntry.getValue();

                List<Integer> staleIds = slots.removeStaleEntries(maxIdleMs);
                total += staleIds.size();

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

        // Remove players without any dimensions
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
