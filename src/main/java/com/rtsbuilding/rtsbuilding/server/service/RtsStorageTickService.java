package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.server.storage.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.RtsHandlerCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

/**
 * Tick-driven cache-refresh service for all active RTS storage sessions.
 *
 * <p>Inspired by AE2's {@code TickManagerService}: each player's storage is
 * refreshed on an <b>adaptive</b> schedule instead of a fixed interval:
 * <ul>
 *   <li>Items keep changing → speed up to every tick (min responsiveness)</li>
 *   <li>Nothing changes for a while → gradually slow down to reduce CPU load</li>
 *   <li>{@link #alert(UUID)} can be called externally to wake up immediately</li>
 * </ul>
 *
 * <p>This avoids trashing the server with per-tick capability lookups for idle
 * players while still providing near-instant updates when storage is active.
 */
public final class RtsStorageTickService {

    public static final RtsStorageTickService INSTANCE = new RtsStorageTickService();

    // ---- adaptive rate constants (see RtsServiceConstants) -------------------

    // ---- state ---------------------------------------------------------------

    /** Per-player aggregate storage instance. */
    private final Map<UUID, RtsAggregateStorage> playerStorage = new HashMap<>();

    /** Per-player handler → cache mappings. */
    private final Map<UUID, List<HandlerCachePair>> playerHandlers = new HashMap<>();

    /** Per-player adaptive tick trackers (replaces old fixed counter). */
    private final Map<UUID, TickTracker> tickTrackers = new HashMap<>();

    private RtsStorageTickService() {
    }

    // ---- lifecycle -------------------------------------------------------------

    /**
     * Registers or updates a player's aggregate storage with the given handlers.
     * Existing caches are reused if the handler identity matches.
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
                cache.update(handler);
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
     * Removes a player's storage cache entirely and releases
     * all cached data for immediate GC.
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
                RtsAe2Compat.releaseNetworkHandler(p.handler);
                RtsBdCompat.releaseNetworkHandler(p.handler);
            }
        }

        this.tickTrackers.remove(uuid);
    }

    // ---- tick (adaptive) -------------------------------------------------------

    /**
     * Called on every server tick for all active players.
     * Uses AE2-style adaptive scheduling: speeds up when busy, slows when idle.
     *
     * @return map of player UUID → set of changed item IDs since last refresh
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

    // ---- alert (like AE2's alertDevice) ----------------------------------------

    /**
     * Wakes up a player's storage ticker immediately, forcing the next refresh
     * to happen without delay. Equivalent to AE2's {@code alertDevice()}.
     * <p>
     * Call this after RTS system insert/extract operations so the GUI reflects
     * changes on the very next tick instead of waiting for the adaptive timer.
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
     * Forces an immediate cache refresh for a specific player and returns
     * the changes. Also resets the adaptive timer to run again next tick.
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

    // ---- accessors -------------------------------------------------------------

    /**
     * Returns the aggregate storage for a player, or {@code null} if not registered.
     */
    public RtsAggregateStorage getStorage(ServerPlayer player) {
        return this.playerStorage.get(player.getUUID());
    }

    /**
     * Calculates the initial refresh rate based on total slot count.
     * <p>
     * Uses a logarithmic formula: {@code rate = ceil(log2(slots / 27 + 1))}.
     * <ul>
     *   <li>1 chest (27 slots) → rate=1 (every tick)</li>
     *   <li>5 chests (135 slots) → rate=3</li>
     *   <li>10 chests (270 slots) → rate=4</li>
     *   <li>100 chests (2700 slots) → rate=7</li>
     * </ul>
     * This ensures smooth scaling: few slots = instant response,
     * many slots = graceful back-off without abrupt threshold jumps.
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

    // ---- value types -----------------------------------------------------------

    record HandlerCachePair(IItemHandler handler, RtsHandlerCache cache) {
    }

    /**
     * Per-player adaptive tick state, analogous to AE2's {@code TickTracker}.
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
