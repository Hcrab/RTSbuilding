package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSyncService;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowTimeoutService;
import com.rtsbuilding.rtsbuilding.server.workflow.service.WorkflowPersistenceService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central workflow engine — the single implementation of {@link IWorkflowEngine}.
 *
 * <p>This engine manages workflow state internally using a per-player
 * {@link RtsWorkflowSlotManager}.  All lifecycle operations go through
 * {@link RtsWorkflowToken} instances that are created by this engine.
 * Events are dispatched to registered listeners via the event bus.</p>
 *
 * <p>The engine is designed as a top-level singleton service.  Obtain the
 * instance via {@link #getInstance()}.</p>
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li><b>Token-only consumer API:</b> External code never touches entries
 *       directly.  All interactions go through {@link RtsWorkflowToken}.</li>
 *   <li><b>Event-driven:</b> Subsystems react to workflow lifecycle events
 *       instead of being wired through explicit callbacks.</li>
 *   <li><b>EntryId-based:</b> All internal lookups use the immutable entry ID,
 *       not the positional index (which shifts on removal).</li>
 *   <li><b>Timeout-safe:</b> {@link RtsWorkflowTimeoutService} periodically
 *       cleans up stale entries to prevent slot exhaustion.</li>
 * </ul>
 */
public final class RtsWorkflowEngine implements IWorkflowEngine {

    private static final RtsWorkflowEngine INSTANCE = new RtsWorkflowEngine();

    // ──────────────────────────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────────────────────────

    /** Per-player, per-dimension slot managers, lazily created. */
    private final Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerSlots = new ConcurrentHashMap<>();

    /**
     * Tracks the most recent valid {@link ServerPlayer} reference per UUID.
     * Updated on every {@code start()}, {@code from()}, and {@code lastActive()} call.
     */
    private final Map<UUID, ServerPlayer> playerRefs = new ConcurrentHashMap<>();

    /** Event bus for lifecycle events. */
    private final RtsWorkflowEventBus eventBus = new RtsWorkflowEventBus();

    /** Network sync service. */
    private final RtsWorkflowSyncService syncService = new RtsWorkflowSyncService();

    /** Optional timeout service (started separately). */
    private RtsWorkflowTimeoutService timeoutService;

    // ──────────────────────────────────────────────────────────────────
    //  Singleton
    // ──────────────────────────────────────────────────────────────────

    private RtsWorkflowEngine() {
    }

    /** Returns the singleton engine instance. */
    public static RtsWorkflowEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the timeout service.  Call once during mod initialisation.
     *
     * @param checkInterval how often to scan for stale workflows
     * @param maxIdleTime   maximum idle time before cleanup
     */
    public void startTimeoutService(Duration checkInterval, Duration maxIdleTime) {
        if (timeoutService == null) {
            timeoutService = new RtsWorkflowTimeoutService(playerSlots, playerRefs, eventBus, syncService);
            timeoutService.start(checkInterval, maxIdleTime);
        }
    }

    /**
     * Stops the timeout service.  Call during mod shutdown.
     */
    public void stopTimeoutService() {
        if (timeoutService != null) {
            timeoutService.stop();
            timeoutService = null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal API (package-private, called by RtsWorkflowToken)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Finds an entry by player UUID, dimension, and entry ID.
     * Package-private — called by {@link RtsWorkflowToken}.
     */
    @Nullable
    RtsWorkflowEntry findEntry(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return null;
        return slots.findEntryById(entryId);
    }

    /**
     * Removes an entry by player UUID, dimension, and entry ID, then notifies the
     * client and fires an event.
     * Package-private — called by {@link RtsWorkflowToken}.
     *
     * <p>Uses {@link RtsWorkflowSlotManager#removeEntryById(int)} to find
     * and remove in a single pass, avoiding a separate index lookup.
     * {@link RtsWorkflowSyncService#notifyPlayer} internally dispatches
     * {@code idle()} when no entries remain, so the caller does not need
     * to check {@code occupiedCount()} beforehand.</p>
     */
    void removeEntry(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return;

        boolean removed = slots.removeEntryById(entryId);
        if (!removed) return;

        // Notify the player via network (notifyPlayer handles idle case internally)
        ServerPlayer player = findPlayerByUUID(playerId);
        if (player != null) {
            syncService.notifyPlayer(player, slots);
        }
    }

    /**
     * Sends a full workflow state update to the player for the given dimension.
     * Package-private — called by {@link RtsWorkflowToken}.
     */
    void notifyPlayer(UUID playerId, ResourceKey<Level> dimension) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return;

        ServerPlayer player = findPlayerByUUID(playerId);
        if (player != null) {
            syncService.notifyPlayer(player, slots);
        }
    }

    /**
     * Fires a lifecycle event.
     * Package-private — called by {@link RtsWorkflowToken}.
     */
    void fireEvent(WorkflowEventType type, UUID playerId, int entryId, RtsWorkflowEntry entry) {
        eventBus.fire(new WorkflowEvent(type, playerId, entryId, entry.snapshot()));
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — Starters
    // ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<RtsWorkflowToken> start(ServerPlayer player,
                                            RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks) {
        if (player == null || type == null) {
            return Optional.empty();
        }
        RtsWorkflowSlotManager slots = getOrCreateSlots(player);
        RtsWorkflowEntry entry = slots.addEntry(priority);
        if (entry == null) {
            String name = player.getGameProfile().getName();
            RtsbuildingMod.LOGGER.warn("[Workflow] {} 工作流已满 ({}), 拒绝新工作流 {}",
                    name, RtsWorkflowSlotManager.MAX_SLOTS, type);
            player.displayClientMessage(
                    Component.literal("§c工作流已满 (" + RtsWorkflowSlotManager.MAX_SLOTS
                            + "/" + RtsWorkflowSlotManager.MAX_SLOTS + "), 无法开始新的操作！"),
                    true);
            return Optional.empty();
        }
        entry.setType(type);
        entry.setTotalBlocks(totalBlocks);

        // Track the player reference for later notification
        playerRefs.put(player.getUUID(), player);

        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowToken token = new RtsWorkflowToken(player.getUUID(), entry.id(), dimension, this);
        fireEvent(WorkflowEventType.STARTED, player.getUUID(), entry.id(), entry);
        syncService.notifyPlayer(player, slots);

        RtsbuildingMod.LOGGER.info("[Workflow] {} 开始工作流 #{}: {} (共 {} 方块)",
                player.getGameProfile().getName(), entry.id(), type, totalBlocks);
        return Optional.of(token);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — Token reconstruction
    // ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<RtsWorkflowToken> from(ServerPlayer player, int entryId) {
        if (player == null) return Optional.empty();
        playerRefs.put(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null || slots.findEntryById(entryId) == null) {
            return Optional.empty();
        }
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entryId, dimension, this));
    }

    @Override
    public Optional<RtsWorkflowToken> lastActive(ServerPlayer player) {
        if (player == null) return Optional.empty();
        playerRefs.put(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return Optional.empty();
        RtsWorkflowEntry entry = slots.lastActive();
        if (entry == null) return Optional.empty();
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entry.id(), dimension, this));
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — Event subscription
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void addListener(WorkflowEventListener listener) {
        eventBus.addListener(listener);
    }

    @Override
    public void removeListener(WorkflowEventListener listener) {
        eventBus.removeListener(listener);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — Queries
    // ──────────────────────────────────────────────────────────────────

    @Override
    public RtsWorkflowStatus getProgress(RtsWorkflowToken token) {
        return token.getProgress();
    }

    @Override
    public RtsWorkflowStatus getProgress(ServerPlayer player, int entryId) {
        if (player == null) return RtsWorkflowStatus.idle();
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return RtsWorkflowStatus.idle();
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) return RtsWorkflowStatus.idle();
        return entry.snapshot();
    }

    @Override
    public List<RtsWorkflowStatus> getAllProgress(ServerPlayer player) {
        if (player == null) return List.of();
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return List.of();
        return slots.occupiedEntries().stream()
                .map(RtsWorkflowEntry::snapshot)
                .toList();
    }

    @Override
    public boolean hasActiveWorkflow(ServerPlayer player) {
        if (player == null) return false;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null && slots.hasActiveWorkflow();
    }

    @Override
    public int activeWorkflowCount(ServerPlayer player) {
        if (player == null) return 0;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null ? slots.activeCount() : 0;
    }

    @Override
    public int occupiedSlotCount(ServerPlayer player) {
        if (player == null) return 0;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null ? slots.occupiedCount() : 0;
    }

    @Override
    public boolean isFull(ServerPlayer player) {
        if (player == null) return false;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null && slots.isFull();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Pipeline integration — fire events without modifying entry
    // ──────────────────────────────────────────────────────────────────

    /**
     * Fires a lifecycle event for an existing workflow entry without
     * modifying the entry itself.
     *
     * <p>Used by the pipeline system to notify listeners when the sync
     * phase of a pipeline completes (success → {@link WorkflowEventType#SYNC_PHASE_COMPLETED}
     * or failure → {@link WorkflowEventType#CANCELLED}).
     * Unlike calling {@link RtsWorkflowToken#complete()} or
     * {@link RtsWorkflowToken#cancel()}, this method does <b>not</b>
     * remove the entry, so the async work (mining batch, placement jobs,
     * etc.) can continue after the pipeline fires SYNC_PHASE_COMPLETED.</p>
     *
     * @param player  the player who owns the workflow
     * @param entryId the immutable entry ID
     * @param type    the event type (typically {@link WorkflowEventType#SYNC_PHASE_COMPLETED}
     *                or {@link WorkflowEventType#CANCELLED})
     */
    public void firePipelineEvent(ServerPlayer player, int entryId, WorkflowEventType type) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null) return;
        fireEvent(type, player.getUUID(), entryId, entry);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — Admin
    // ──────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────
    //  Pause / Resume — per-entry valve
    // ──────────────────────────────────────────────────────────────────

    @Override
    public boolean isEntryPaused(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        if (playerId == null || dimension == null) return false;
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return false;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        return entry != null && entry.paused();
    }

    /**
     * Pauses all active (non-suspended, non-paused) workflow entries for the
     * given player across all dimensions.
     *
     * <p>Used when the player disables RTS mode or disconnects without manually
     * pausing their threads. Already-paused and suspended entries are left
     * untouched.</p>
     *
     * @param playerId the player's UUID
     * @param notify   whether to send a network sync to the player (no-op if offline)
     */
    public void pauseAllActive(UUID playerId, boolean notify) {
        if (playerId == null) return;
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        if (dimMap == null) return;

        for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : dimMap.entrySet()) {
            ResourceKey<Level> dimension = dimEntry.getKey();
            RtsWorkflowSlotManager slots = dimEntry.getValue();
            boolean anyChanged = false;

            for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
                if (!entry.suspended() && !entry.paused()) {
                    entry.setPaused(true);
                    fireEvent(WorkflowEventType.PAUSED, playerId, entry.id(), entry);
                    anyChanged = true;
                }
            }

            if (anyChanged && notify) {
                ServerPlayer player = findPlayerByUUID(playerId);
                if (player != null) {
                    syncService.notifyPlayer(player, slots);
                }
            }
        }
    }

    @Override
    public void deleteWorkflow(ServerPlayer player, int entryId) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;

        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) return;

        RtsbuildingMod.LOGGER.info("[Workflow] {} 删除工作流 #{}: {}",
                player.getGameProfile().getName(), entry.id(), entry.type());

        fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), entryId, entry);
        slots.removeEntryById(entryId);

        if (slots.occupiedCount() > 0) {
            syncService.notifyPlayer(player, slots);
        } else {
            syncService.sendIdle(player);
        }
    }

    @Override
    public void cancelAll(ServerPlayer player) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;

        for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
            fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), entry.id(), entry);
        }
        slots.clear();
        syncService.sendIdle(player);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — World-switch cleanup
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void clearPlayerData(UUID playerId) {
        if (playerId == null) return;
        playerSlots.remove(playerId);
        playerRefs.remove(playerId);
    }

    @Override
    public void clearAllData() {
        int totalPlayers = playerSlots.size();
        playerSlots.clear();
        playerRefs.clear();
        RtsbuildingMod.LOGGER.info("[Workflow] Cleared all workflow data ({} players)", totalPlayers);
    }

    /**
     * 将持久化委托给 {@link WorkflowPersistenceService}。
     */
    public void saveAll(MinecraftServer server) {
        WorkflowPersistenceService.getInstance().saveAll(server, playerSlots);
    }

    /**
     * 从世界存档加载玩家工作流并合并到内存。
     */
    public void loadPlayerFromStore(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return;
        UUID playerId = player.getUUID();

        Map<ResourceKey<Level>, RtsWorkflowSlotManager> loaded =
                WorkflowPersistenceService.getInstance().loadPlayerFromStore(server, playerId);

        if (loaded.isEmpty()) return;

        // Merge loaded slot managers into the engine's in-memory map
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimMap = playerSlots
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> entry : loaded.entrySet()) {
            ResourceKey<Level> dimension = entry.getKey();
            if (!dimMap.containsKey(dimension)) {
                dimMap.put(dimension, entry.getValue());
            }
        }

        // Notify the client so the UI shows the restored entries
        ResourceKey<Level> currentDim = player.level().dimension();
        RtsWorkflowSlotManager currentSlots = getSlots(playerId, currentDim);
        if (currentSlots != null && currentSlots.occupiedCount() > 0) {
            syncService.notifyPlayer(player, currentSlots);
        }

        RtsbuildingMod.LOGGER.info("[Workflow] Loaded {} workflow entries for player {} from store",
                loaded.values().stream().mapToInt(RtsWorkflowSlotManager::occupiedCount).sum(),
                playerId);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Gets or creates a slot manager for the given player in the player's current dimension.
     */
    private RtsWorkflowSlotManager getOrCreateSlots(ServerPlayer player) {
        playerRefs.put(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        return playerSlots
                .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(dimension, k -> new RtsWorkflowSlotManager());
    }

    /**
     * Gets the slot manager for the given player and dimension, or {@code null} if none exists.
     */
    @Nullable
    private RtsWorkflowSlotManager getSlots(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        if (dimMap == null) return null;
        return dimMap.get(dimension);
    }

    /**
     * Finds a ServerPlayer by UUID.  First checks the tracked player refs,
     * then falls back to scanning the Minecraft server's player list.
     * Returns null if the player is offline or not found.
     */
    @Nullable
    private ServerPlayer findPlayerByUUID(UUID playerId) {
        // Check our tracked reference first
        ServerPlayer cached = playerRefs.get(playerId);
        if (cached != null && cached.level() != null && !cached.level().isClientSide()) {
            return cached;
        }
        // Fallback: scan the server's player list
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                playerRefs.put(playerId, online);
                return online;
            }
        }
        // Player is offline — remove stale reference
        playerRefs.remove(playerId);
        return null;
    }
}
