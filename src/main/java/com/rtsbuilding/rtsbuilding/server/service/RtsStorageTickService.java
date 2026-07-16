package com.rtsbuilding.rtsbuilding.server.service;

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
 *   <li>Items keep changing ??speed up to every tick (min responsiveness)</li>
 *   <li>Nothing changes for a while ??gradually slow down to reduce CPU load</li>
 *   <li>{@link #alert(UUID)} can be called externally to wake up immediately</li>
 * </ul>
 *
 * <p>This avoids trashing the server with per-tick capability lookups for idle
 * players while still providing near-instant updates when storage is active.
 */
public final class RtsStorageTickService {

    public static final RtsStorageTickService INSTANCE = new RtsStorageTickService();

    // ---- adaptive rate constants ---------------------------------------------

    /** Fastest: refresh every tick (50ms at 20 TPS). */
    private static final int MIN_TICK_RATE = 1;

    /** Slowest: refresh every 60 ticks (3s at 20 TPS) when fully idle. */
    private static final int MAX_TICK_RATE = 60;

    /** Starting rate after registration or alert. */
    private static final int DEFAULT_TICK_RATE = 8;

    /**
     * Maximum initial rate based on slot count.
     * Even huge AE2 systems start at most this rate; the adaptive
     * mechanism quickly speeds up if changes are detected.
     */
    private static final int MAX_INITIAL_RATE = 8;

    /**
     * How many consecutive idle cycles before slowing down.
     * At default rate of 8, this is 15 × 8 = 120 ticks (6s) of no activity
     * before we start increasing the interval.
     */
    private static final int IDLE_THRESHOLD = 15;

    // ---- state ---------------------------------------------------------------

    /** Per-player aggregate storage instance. */
    private final Map<UUID, RtsAggregateStorage> playerStorage = new HashMap<>();

    /** Per-player handler ??cache mappings. */
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
        if (player == null) return null;
        return registerPlayer(player.getUUID(), handlers);
    }

    /** 以玩家 ID 注册处理器，供生命周期边界和无游戏运行时的回归测试复用。 */
    public RtsAggregateStorage registerPlayer(UUID uuid, List<IItemHandler> handlers) {
        if (uuid == null) return null;
        List<IItemHandler> normalizedHandlers = distinctByIdentity(handlers);
        RtsAggregateStorage storage = this.playerStorage.computeIfAbsent(uuid, k -> new RtsAggregateStorage());

        // Unmount stale handlers
        List<HandlerCachePair> existing = this.playerHandlers.getOrDefault(uuid, List.of());
        Set<IItemHandler> newSet = Collections.newSetFromMap(new IdentityHashMap<>());
        newSet.addAll(normalizedHandlers);

        // Unmount removed handlers
        for (HandlerCachePair p : existing) {
            if (!newSet.contains(p.handler)) {
                storage.unmount(p.handler);
                p.cache.release();
            }
        }

        // Mount new handlers (reuse existing cache if available)
        Map<IItemHandler, RtsHandlerCache> cacheMap = new IdentityHashMap<>();
        for (HandlerCachePair p : existing) {
            cacheMap.put(p.handler, p.cache);
        }

        List<HandlerCachePair> newPairs = new ArrayList<>();
        for (int priority = 0; priority < normalizedHandlers.size(); priority++) {
            IItemHandler handler = normalizedHandlers.get(priority);
            RtsHandlerCache cache = cacheMap.getOrDefault(handler, new RtsHandlerCache());
            if (!cacheMap.containsKey(handler)) {
                storage.mount(normalizedHandlers.size() - priority, handler, cache); // reverse priority: first = highest
                // Immediately populate the cache so page builds don't skip this handler
                cache.update(handler);
            }
            newPairs.add(new HandlerCachePair(handler, cache));
        }

        this.playerHandlers.put(uuid, newPairs);
        // Initialize tracker with initial rate based on handler count
        int initialRate = calculateInitialRate(normalizedHandlers);
        this.tickTrackers.computeIfAbsent(uuid, k -> new TickTracker(initialRate));
        return storage;
    }

    /**
     * 在端点租约销毁处理器前，先从聚合存储中卸载这个借用引用。
     *
     * <p>本方法不销毁处理器本身；端点租约仍是 AE/RS 网络处理器的唯一所有者。</p>
     */
    public boolean detachHandler(UUID playerId, IItemHandler handler) {
        if (playerId == null || handler == null) return false;
        List<HandlerCachePair> existing = this.playerHandlers.get(playerId);
        if (existing == null || existing.isEmpty()) return false;

        List<HandlerCachePair> retained = new ArrayList<>(existing.size());
        boolean detached = false;
        RtsAggregateStorage storage = this.playerStorage.get(playerId);
        for (HandlerCachePair pair : existing) {
            if (pair.handler == handler) {
                if (storage != null) storage.unmount(pair.handler);
                pair.cache.release();
                detached = true;
            } else {
                retained.add(pair);
            }
        }
        if (detached) this.playerHandlers.put(playerId, retained);
        return detached;
    }

    /**
     * Removes a player's storage cache entirely and releases
     * all cached data for immediate GC.
     */
    public void unregisterPlayer(ServerPlayer player) {
        if (player == null) return;
        unregisterPlayer(player.getUUID());
    }

    /** 仅释放本服务拥有的聚合缓存和快照，不销毁端点处理器。 */
    public void unregisterPlayer(UUID uuid) {
        if (uuid == null) return;
        this.playerStorage.remove(uuid);

        // Release cache data structures proactively so the GC can
        // reclaim the large slot/count arrays before the cache objects
        // themselves become unreachable.
        List<HandlerCachePair> pairs = this.playerHandlers.remove(uuid);
        if (pairs != null) {
            for (HandlerCachePair p : pairs) {
                p.cache.release();
            }
        }

        this.tickTrackers.remove(uuid);
    }

    // ---- tick (adaptive) -------------------------------------------------------

    /**
     * Called on every server tick for all active players.
     * Uses AE2-style adaptive scheduling: speeds up when busy, slows when idle.
     *
     * @return map of player UUID ??set of changed item IDs since last refresh
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
                // ── Changes detected ??speed up like AE2's URGENT/FASTER ──
                tracker.currentRate = Math.max(MIN_TICK_RATE, tracker.currentRate / 2);
                tracker.consecutiveIdle = 0;
                allChanges.put(uuid, changes);
            } else {
                // ── No changes ??gradually slow down like AE2's IDLE ──
                tracker.consecutiveIdle++;
                if (tracker.consecutiveIdle > IDLE_THRESHOLD) {
                    tracker.currentRate = Math.min(MAX_TICK_RATE, tracker.currentRate + 1);
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
            tracker.currentRate = MIN_TICK_RATE;
            tracker.ticksSinceRefresh = MIN_TICK_RATE; // Will trigger on next tick
            tracker.consecutiveIdle = 0;
        }
    }

    /**
     * Forces an immediate cache refresh for a specific player and returns
     * the changes. Also resets the adaptive timer to run again next tick.
     */
    public Set<String> forceRefresh(ServerPlayer player) {
        if (player == null) return Set.of();
        return forceRefresh(player.getUUID());
    }

    /** 以玩家 ID 强制刷新，避免生命周期清理依赖仍存活的玩家实体。 */
    public Set<String> forceRefresh(UUID uuid) {
        if (uuid == null) return Set.of();
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
        if (player == null) return null;
        return this.playerStorage.get(player.getUUID());
    }

    private static List<IItemHandler> distinctByIdentity(List<IItemHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) return List.of();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IItemHandler> result = new ArrayList<>(handlers.size());
        for (IItemHandler handler : handlers) {
            if (handler != null && seen.add(handler)) result.add(handler);
        }
        return result;
    }

    /**
     * Calculates the initial refresh rate based on total slot count.
     * <p>
     * Uses a logarithmic formula: {@code rate = ceil(log2(slots / 27 + 1))}.
     * <ul>
     *   <li>1 chest (27 slots) ??rate=1 (every tick)</li>
     *   <li>5 chests (135 slots) ??rate=3</li>
     *   <li>10 chests (270 slots) ??rate=4</li>
     *   <li>100 chests (2700 slots) ??rate=7</li>
     * </ul>
     * This ensures smooth scaling: few slots = instant response,
     * many slots = graceful back-off without abrupt threshold jumps.
     */
    private static int calculateInitialRate(List<IItemHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) return DEFAULT_TICK_RATE;
        int totalSlots = 0;
        for (IItemHandler h : handlers) {
            try {
                totalSlots += h.getSlots();
            } catch (Exception ignored) {
            }
        }
        if (totalSlots <= 0) return MIN_TICK_RATE;
        // Logarithmic scaling: rate = ceil(log2(slots / 27 + 1))
        // 27 is one chest's slot count, used as the base unit.
        double logValue = Math.log((double) totalSlots / 27.0 + 1.0) / Math.log(2.0);
        int rate = (int) Math.ceil(logValue);
        return Math.max(MIN_TICK_RATE, Math.min(MAX_INITIAL_RATE, rate));
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
