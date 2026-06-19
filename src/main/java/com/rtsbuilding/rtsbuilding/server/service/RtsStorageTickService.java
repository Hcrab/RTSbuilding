package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsHandlerCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick 驱动的自适应缓存刷新服务，管理所有活跃 RTS 存储会话的缓存。
 *
 * <p>受 AE2 的 {@code TickManagerService} 启发，每个玩家的存储以<b>自适应</b>
 * 计划刷新，而非固定间隔：物品频繁变化时加速到每 tick 刷新以最小化响应时间，
 * 长时间无变化时逐渐减速以减少 CPU 负载。外部可通过 {@link #alert(UUID)} 立即唤醒。
 *
 * <p><b>核心数据：</b>
 * <ul>
 *   <li>{@link #playerStorage} — 每玩家的 {@link RtsAggregateStorage} 聚合缓存实例</li>
 *   <li>{@link #playerHandlers} — 每玩家的 {@link IItemHandler} → {@link RtsHandlerCache} 映射</li>
 *   <li>{@link #tickTrackers} — 每玩家的 {@link TickTracker} 自适应 tick 状态</li>
 * </ul>
 *
 * <p><b>生命周期方法：</b>
 * <ul>
 *   <li>{@link #registerPlayer(ServerPlayer, List)} — 注册玩家，挂载处理器、
 *       重用现有缓存或创建新缓存、计算初始刷新率</li>
 *   <li>{@link #unregisterPlayer(ServerPlayer)} — 完全移除玩家，释放缓存数据、
 *       释放 AE2/BD 网络处理器引用以加速 GC 回收</li>
 * </ul>
 *
 * <p><b>自适应 tick 方法：</b>
 * <ul>
 *   <li>{@link #tick()} — 每服务器 tick 调用，检查每个玩家的定时器，
 *       检测到变化时加速（currentRate / 2），空闲超过 IDLE_THRESHOLD 时减速（+1）</li>
 *   <li>{@link #alert(UUID)} — 立即将玩家速率设为 MIN_TICK_RATE，
 *       强制在下个 tick 刷新（等效 AE2 的 alertDevice）</li>
 *   <li>{@link #forceRefresh(ServerPlayer)} — 强制立即刷新并返回变更集</li>
 * </ul>
 *
 * <p><b>初始速率计算：</b>使用对数公式 {@code rate = ceil(log2(slots / 27 + 1))}，
 * 1 个箱子（27 槽位）→ 每 tick，10 个箱子 → 每 4 tick，100 个箱子 → 每 7 tick。
 * 确保少槽位即时响应，多槽位优雅退避。
 *
 * <p><b>内部记录：</b>{@link TickTracker} 跟踪当前速率、自上次刷新以来的 tick 数、
 * 连续空闲次数。{@link HandlerCachePair} 记录处理器和缓存的配对关系。
 */
public final class RtsStorageTickService {

    public static final RtsStorageTickService INSTANCE = new RtsStorageTickService();

    // ---- 自适应速率常量（见 RtsServiceConstants）---------------------------

    // ---- state ---------------------------------------------------------------

    /** 每玩家的聚合存储实例。 */
    private final Map<UUID, RtsAggregateStorage> playerStorage = new ConcurrentHashMap<>();

    /** 每玩家的处理器 → 缓存映射。 */
    private final Map<UUID, List<HandlerCachePair>> playerHandlers = new ConcurrentHashMap<>();

    /** 每玩家的自适应 tick 跟踪器（替换旧的固定计数器）。 */
    private final Map<UUID, TickTracker> tickTrackers = new ConcurrentHashMap<>();

    private RtsStorageTickService() {
    }

    // ---- 生命周期 -------------------------------------------------------------

    /**
     * 注册或更新玩家的聚合存储以及给定的处理器。
     * 如果处理器身份匹配，则重用现有缓存。
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
     * 完全移除玩家的存储缓存，释放所有缓存数据以便立即 GC。
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

    // ---- tick（自适应）------------------------------------------------------

    /**
     * 每个服务器 tick 对所有活跃玩家调用。
     * 使用 AE2 风格的自适应调度：繁忙时加速，空闲时减速。
     *
     * @return 玩家 UUID → 自上次刷新以来变化的物品 ID 集合的映射
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

    // ---- alert（类似 AE2 的 alertDevice）--------------------------------------

    /**
     * 立即唤醒玩家的存储 tick 器，强制下一次刷新
     * 无延迟地发生。相当于 AE2 的 {@code alertDevice()}。
     * <p>
     * 在 RTS 系统插入/提取操作后调用此方法，
     * 以便 GUI 在下一个 tick 就反映更改，
     * 而不是等待自适应定时器。
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
     * 强制立即为特定玩家刷新缓存并返回更改。
     * 同时重置自适应定时器，以便在下一个 tick 再次运行。
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

    // ---- 访问器 -------------------------------------------------------------

    /**
     * 返回玩家的聚合存储，如果未注册则返回 {@code null}。
     */
    public RtsAggregateStorage getStorage(ServerPlayer player) {
        return this.playerStorage.get(player.getUUID());
    }

    /**
     * 基于总槽位数计算初始刷新率。
     * <p>
     * 使用对数公式：{@code rate = ceil(log2(slots / 27 + 1))}。
     * <ul>
     *   <li>1 个箱子（27 槽位）→ rate=1（每 tick）</li>
     *   <li>5 个箱子（135 槽位）→ rate=3</li>
     *   <li>10 个箱子（270 槽位）→ rate=4</li>
     *   <li>100 个箱子（2700 槽位）→ rate=7</li>
     * </ul>
     * 这确保了平滑的缩放：少槽位=即时响应，
     * 多槽位=优雅的后退，没有突变的阈值跳跃。
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

    // ---- 值类型 -----------------------------------------------------------

    record HandlerCachePair(IItemHandler handler, RtsHandlerCache cache) {
    }

    /**
     * 每玩家自适应 tick 状态，类似于 AE2 的 {@code TickTracker}。
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
