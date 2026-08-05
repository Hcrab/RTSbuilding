package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsHandlerCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick-driven adaptive cache refresh service, managing caches for all active RTS storage sessions.
 *
 * <p>Inspired by AE2's {@code TickManagerService}, each player's storage is refreshed on an <b>adaptive</b>
 * schedule rather than a fixed interval: speeds up to every tick when items change frequently to minimize response time,
 * gradually slows down during long idle periods to reduce CPU load. External code can wake it up immediately via {@link #alert(UUID)}.
 *
 * <p><b>Core data:</b>
 * <ul>
 *   <li>{@link #playerStorage} — Per-player {@link RtsAggregateStorage} aggregate cache instances</li>
 *   <li>{@link #playerHandlers} — Per-player {@link IItemHandler} → {@link RtsHandlerCache} mappings</li>
 *   <li>{@link #tickTrackers} — Per-player {@link TickTracker} adaptive tick state</li>
 * </ul>
 *
 * <p><b>Lifecycle methods:</b>
 * <ul>
 *   <li>{@link #registerPlayer(ServerPlayer, List)} — Registers a player, mounts handlers,
 *       reuses existing cache or creates new cache, calculates initial refresh rate</li>
 *   <li>{@link #unregisterPlayer(ServerPlayer)} — Completely removes a player, releases cache data,
 *       releases AE2/BD network handler references to accelerate GC reclaim</li>
 * </ul>
 *
 * <p><b>Adaptive tick methods:</b>
 * <ul>
 *   <li>{@link #tick()} — Called every server tick, checks each player's timer,
 *       speeds up when changes detected (currentRate / 2), slows down when idle exceeds IDLE_THRESHOLD (+1)</li>
 *   <li>{@link #alert(UUID)} — Immediately sets player rate to MIN_TICK_RATE,
 *       forces a refresh on the next tick (equivalent to AE2's alertDevice)</li>
 *   <li>{@link #forceRefresh(ServerPlayer)} — Forces an immediate refresh and returns the change set</li>
 * </ul>
 *
 * <p><b>Initial rate calculation:</b> Uses logarithmic formula {@code rate = ceil(log2(slots / 27 + 1))},
 * 1 chest (27 slots) → every tick, 10 chests → every 4 ticks, 100 chests → every 7 ticks.
 * Ensures instant response for few slots, graceful backoff for many slots.
 *
 * <p><b>Internal records:</b> {@link TickTracker} tracks current rate, ticks since last refresh,
 * consecutive idle count. {@link HandlerCachePair} records handler and cache pairings.
 */
public final class RtsStorageTickService {

    public static final RtsStorageTickService INSTANCE = new RtsStorageTickService();

    // ---- Adaptive rate constants (see RtsServiceConstants) ---------------------------

    // ---- state ---------------------------------------------------------------

    /** Per-player aggregate storage instances. */
    private final Map<UUID, RtsAggregateStorage> playerStorage = new ConcurrentHashMap<>();

    /** Per-player handler → cache mappings. */
    private final Map<UUID, List<HandlerCachePair>> playerHandlers = new ConcurrentHashMap<>();

    /** Per-player adaptive tick trackers (replacing old fixed counters). */
    private final Map<UUID, TickTracker> tickTrackers = new ConcurrentHashMap<>();

    private RtsStorageTickService() {
    }

    // ---- Lifecycle -------------------------------------------------------------

    /**
     * Registers or updates a player's aggregate storage with the given handlers.
     * Reuses existing cache if handler identity matches.
     */
    public RtsAggregateStorage registerPlayer(ServerPlayer player, List<IItemHandler> handlers) {
        UUID uuid = player.getUUID();
        RtsAggregateStorage storage = this.playerStorage.computeIfAbsent(uuid, k -> new RtsAggregateStorage());

        // Unmount stale handlers
        List<HandlerCachePair> existing = this.playerHandlers.getOrDefault(uuid, List.of());
        Set<IItemHandler> existingSet = new HashSet<>();
        for (HandlerCachePair p : existing) {
            existingSet.add(p.handler);
        }
        Set<IItemHandler> newSet = new HashSet<>(handlers);

        // Unmount removed handlers
        for (HandlerCachePair p : existing) {
            if (!newSet.contains(p.handler)) {
                storage.unmount(p.handler);
            }
        }

        // Mount new handlers (reuse existing cache if available)
        Map<IItemHandler, RtsHandlerCache> cacheMap = new HashMap<>();
        for (HandlerCachePair p : existing) {
            cacheMap.put(p.handler, p.cache);
        }

        List<HandlerCachePair> newPairs = new ArrayList<>();
        for (int priority = 0; priority < handlers.size(); priority++) {
            IItemHandler handler = handlers.get(priority);
            RtsHandlerCache cache = cacheMap.getOrDefault(handler, new RtsHandlerCache());
            if (!cacheMap.containsKey(handler)) {
                storage.mount(handlers.size() - priority, handler, cache); // reverse priority: first = highest
                // Immediately populate the cache so page builds don't skip this handler
                long perfUpdateNanos = System.nanoTime();
                cache.update(handler);
                long perfUpdateMs = (System.nanoTime() - perfUpdateNanos) / 1_000_000L;
                if (perfUpdateMs >= 30L) {
                    com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info(
                            "RTS-PERF: registerPlayer.cache.update took {} ms (slots={}, handler={})",
                            perfUpdateMs, cache.getCachedSlotCount(), handler.getClass().getSimpleName());
                }
            }
            newPairs.add(new HandlerCachePair(handler, cache));
        }

        this.playerHandlers.put(uuid, newPairs);
        // Initialize tracker with initial rate based on handler count
        int initialRate = calculateInitialRate(handlers);
        this.tickTrackers.computeIfAbsent(uuid, k -> new TickTracker(initialRate));
        return storage;
    }

    /**
     * Completely removes a player's storage cache, releasing all cache data for immediate GC.
     */
    public void unregisterPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        this.playerStorage.remove(uuid);

        // Release cache data structures proactively so the GC can
        // reclaim the large slot/count arrays before the cache objects
        // themselves become unreachable.
        List<HandlerCachePair> pairs = this.playerHandlers.remove(uuid);
        if (pairs != null) {
            for (HandlerCachePair p : pairs) {
                p.cache.release();
                // Release the handler's own heavy references (e.g. AE2's
                // ServerPlayer and IStorageService references, or BD's
                // internal caches) so the GC can reclaim them immediately
                // instead of waiting for the handler object itself to become
                // unreachable.
                for (var provider : RtsCompatRegistry.getStorageProviders()) {
                    provider.releaseItemHandler(p.handler);
                }
            }
        }

        this.tickTrackers.remove(uuid);
    }

    // ---- Tick (adaptive) ------------------------------------------------------

    /**
     * Called every server tick for all active players.
     * Uses AE2-style adaptive scheduling: speeds up when busy, slows down when idle.
     *
     * @return Map of player UUID to the set of item IDs that changed since last refresh
     */
    public Map<UUID, Set<String>> tick() {
        Map<UUID, Set<String>> allChanges = new HashMap<>();

        for (UUID uuid : this.playerHandlers.keySet()) {
            TickTracker tracker = this.tickTrackers.get(uuid);
            if (tracker == null) continue;

            // Check if it's time for this player's next refresh
            tracker.ticksSinceRefresh++;
            if (tracker.ticksSinceRefresh < tracker.currentRate) {
                continue;
            }
            tracker.ticksSinceRefresh = 0;

            RtsAggregateStorage storage = this.playerStorage.get(uuid);
            if (storage == null) continue;

            Set<String> changes = storage.tickUpdate();

            if (!changes.isEmpty()) {
                // ── Changes detected → speed up like AE2's URGENT/FASTER ──
                tracker.currentRate = Math.max(RtsServiceConstants.MIN_TICK_RATE, tracker.currentRate / 2);
                tracker.consecutiveIdle = 0;
                allChanges.put(uuid, changes);
            } else {
                // ── No changes → gradually slow down like AE2's IDLE ──
                tracker.consecutiveIdle++;
                if (tracker.consecutiveIdle > RtsServiceConstants.IDLE_THRESHOLD) {
                    tracker.currentRate = Math.min(RtsServiceConstants.MAX_TICK_RATE, tracker.currentRate + 1);
                }
            }
        }

        return allChanges;
    }

    // ---- Alert (similar to AE2's alertDevice) --------------------------------------

    /**
     * Immediately wakes up the player's storage ticker, forcing the next refresh
     * to happen without delay. Equivalent to AE2's {@code alertDevice()}.
     * <p>
     * Call this method after RTS system insert/extract operations,
     * so that the GUI reflects changes on the next tick,
     * rather than waiting for the adaptive timer.
     */
    public void alert(UUID playerUuid) {
        TickTracker tracker = this.tickTrackers.get(playerUuid);
        if (tracker != null) {
            tracker.currentRate = RtsServiceConstants.MIN_TICK_RATE;
            tracker.ticksSinceRefresh = RtsServiceConstants.MIN_TICK_RATE; // Will trigger on next tick
            tracker.consecutiveIdle = 0;
        }
    }

    /**
     * Forces an immediate cache refresh for a specific player and returns changes.
     * Also resets the adaptive timer so it runs again on the next tick.
     */
    public Set<String> forceRefresh(ServerPlayer player) {
        UUID uuid = player.getUUID();
        RtsAggregateStorage storage = this.playerStorage.get(uuid);
        if (storage == null) return Set.of();

        TickTracker tracker = this.tickTrackers.get(uuid);
        if (tracker != null) {
            tracker.ticksSinceRefresh = tracker.currentRate; // Force immediate on next tick too
        }
        return storage.tickUpdate();
    }

    // ---- Accessors -------------------------------------------------------------

    /**
     * Returns the player's aggregate storage, or {@code null} if not registered.
     */
    public RtsAggregateStorage getStorage(ServerPlayer player) {
        return this.playerStorage.get(player.getUUID());
    }

    /**
     * Returns the slot cache currently registered for the given raw item handler,
     * or {@code null} if the handler is not registered (e.g. no linked storage).
     * <p>Used by page building to reuse already-cached slot snapshots instead of
     * calling {@code getStackInSlot()} per slot again.
     */
    public RtsHandlerCache getHandlerCache(UUID playerUuid, IItemHandler handler) {
        if (playerUuid == null || handler == null) {
            return null;
        }
        List<HandlerCachePair> pairs = this.playerHandlers.get(playerUuid);
        if (pairs == null) {
            return null;
        }
        for (HandlerCachePair pair : pairs) {
            if (pair.handler == handler) {
                return pair.cache;
            }
        }
        return null;
    }

    /**
     * Calculates the initial refresh rate based on total slot count.
     * <p>
     * Uses logarithmic formula: {@code rate = ceil(log2(slots / 27 + 1))}.
     * <ul>
     *   <li>1 chest (27 slots) → rate=1 (every tick)</li>
     *   <li>5 chests (135 slots) → rate=3</li>
     *   <li>10 chests (270 slots) → rate=4</li>
     *   <li>100 chests (2700 slots) → rate=7</li>
     * </ul>
     * This ensures smooth scaling: few slots = instant response,
     * many slots = graceful backoff, without abrupt threshold jumps.
     */
    private static int calculateInitialRate(List<IItemHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) return RtsServiceConstants.DEFAULT_TICK_RATE;
        int totalSlots = 0;
        for (IItemHandler h : handlers) {
            try {
                totalSlots += h.getSlots();
            } catch (Exception ignored) {
            }
        }
        if (totalSlots <= 0) return RtsServiceConstants.MIN_TICK_RATE;
        // Logarithmic scaling: rate = ceil(log2(slots / 27 + 1))
        // 27 is one chest's slot count, used as the base unit.
        double logValue = Math.log((double) totalSlots / 27.0 + 1.0) / Math.log(2.0);
        int rate = (int) Math.ceil(logValue);
        return Math.max(RtsServiceConstants.MIN_TICK_RATE, Math.min(RtsServiceConstants.MAX_INITIAL_RATE, rate));
    }

    // ---- Value types -----------------------------------------------------------

    record HandlerCachePair(IItemHandler handler, RtsHandlerCache cache) {
    }

    /**
     * Per-player adaptive tick state, similar to AE2's {@code TickTracker}.
     */
    private static final class TickTracker {
        /** Current adaptive rate (ticks between refreshes). */
        int currentRate;
        /** Ticks elapsed since the last refresh. */
        int ticksSinceRefresh = 0;
        /** Consecutive refresh cycles with zero changes. */
        int consecutiveIdle = 0;

        TickTracker(int initialRate) {
            this.currentRate = initialRate;
        }
    }
}
