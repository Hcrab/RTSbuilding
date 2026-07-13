package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskScheduler;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 仅在 OP 从开发者任务页启动场景后工作的服务端指标窗口。
 *
 * <p>关闭时，各热路径只做一次哈希表查询；不会扫描任务、页面或储存。开启后采样读取
 * Task Engine、掉落缓存和真实页面/端点事件，不读取物品 NBT、聊天或服务器地址。</p>
 */
public final class RtsDeveloperMetrics {
    private static final Map<UUID, ActiveRun> ACTIVE = new ConcurrentHashMap<>();

    private RtsDeveloperMetrics() {
    }

    public static boolean begin(ServerPlayer player, String runId, String task) {
        return player != null && begin(player.getUUID(), runId, task);
    }

    static boolean begin(UUID playerId, String runId, String task) {
        if (playerId == null || runId == null || task == null) return false;
        ActiveRun requested = new ActiveRun(runId, task, new MutableMetrics());
        ActiveRun existing = ACTIVE.putIfAbsent(playerId, requested);
        return existing == null || (existing.runId().equals(runId) && existing.task().equals(task));
    }

    public static FinishResult finish(ServerPlayer player, String runId, String task) {
        return finish(player == null ? null : player.getUUID(), runId, task);
    }

    static FinishResult finish(UUID playerId, String runId, String task) {
        if (playerId == null || runId == null || task == null) return FinishResult.REJECTED;
        ActiveRun active = ACTIVE.get(playerId);
        if (active == null || !active.runId().equals(runId) || !active.task().equals(task)) {
            return FinishResult.REJECTED;
        }
        if (!ACTIVE.remove(playerId, active)) return FinishResult.REJECTED;
        return new FinishResult(true, active.metrics().snapshot());
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) ACTIVE.remove(playerId);
    }

    public static void recordTaskTick(MinecraftServer server, TaskScheduler.TickStats stats) {
        if (server == null || stats == null || ACTIVE.isEmpty()) return;
        for (var entry : ACTIVE.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            var tasks = RtsTaskEngine.INSTANCE.diagnostics(player.getUUID());
            var session = ServiceRegistry.getInstance().session().getIfPresent(player);
            BufferSample bufferSample = BufferSample.EMPTY;
            if (session != null) {
                var buffer = session.miningDropBuffer;
                long age = buffer.firstQueuedGameTime < 0L ? 0L
                        : Math.max(0L, player.serverLevel().getGameTime() - buffer.firstQueuedGameTime);
                bufferSample = new BufferSample(buffer.bufferedItems, buffer.stacks.size(), age);
            }
            recordTaskSample(entry.getKey(), stats, tasks, bufferSample);
        }
    }

    static void recordTaskSample(UUID playerId, TaskScheduler.TickStats stats,
            RtsTaskEngine.TaskDiagnostics tasks, BufferSample buffer) {
        ActiveRun run = playerId == null ? null : ACTIVE.get(playerId);
        MutableMetrics metrics = run == null ? null : run.metrics();
        if (metrics == null || stats == null || tasks == null || buffer == null) return;
        metrics.tickSamples++;
        metrics.tickNanos += Math.max(0L, stats.elapsedNanos());
        metrics.maxTickNanos = Math.max(metrics.maxTickNanos, Math.max(0L, stats.elapsedNanos()));
        metrics.processedUnits += Math.max(0, stats.processedUnits());
        metrics.slices += Math.max(0, stats.slices());
        if (stats.timeBudgetExhausted()) metrics.timeBudgetExhausted++;
        if (stats.unitBudgetExhausted()) metrics.unitBudgetExhausted++;
        tasks.activeByType().forEach((type, count) -> metrics.maxActive.merge(type, count, Math::max));
        tasks.waitingByType().forEach((type, count) -> metrics.maxWaiting.merge(type, count, Math::max));
        metrics.bufferItems = Math.max(0, buffer.items());
        metrics.bufferStacks = Math.max(0, buffer.stacks());
        metrics.maxBufferItems = Math.max(metrics.maxBufferItems, metrics.bufferItems);
        metrics.maxBufferStacks = Math.max(metrics.maxBufferStacks, metrics.bufferStacks);
        metrics.bufferAgeTicks = Math.max(0L, buffer.ageTicks());
        metrics.maxBufferAgeTicks = Math.max(metrics.maxBufferAgeTicks, metrics.bufferAgeTicks);
    }

    public static void recordPageBuild(ServerPlayer player) { mutate(player, m -> m.pageBuilds++); }
    public static void recordPageSend(ServerPlayer player) { mutate(player, m -> m.pageSends++); }
    public static void recordEndpointRebuild(UUID playerId) { mutate(playerId, m -> m.endpointRebuilds++); }
    public static void recordEndpointReuse(UUID playerId) { mutate(playerId, m -> m.endpointReuses++); }
    public static void recordBufferFallback(ServerPlayer player) { mutate(player, m -> m.bufferFallbacks++); }

    static void recordPageBuild(UUID playerId) { mutate(playerId, m -> m.pageBuilds++); }
    static void recordPageSend(UUID playerId) { mutate(playerId, m -> m.pageSends++); }
    static void recordBufferFallback(UUID playerId) { mutate(playerId, m -> m.bufferFallbacks++); }

    private static void mutate(ServerPlayer player, Consumer<MutableMetrics> action) {
        if (player != null) mutate(player.getUUID(), action);
    }

    private static void mutate(UUID playerId, Consumer<MutableMetrics> action) {
        if (playerId == null) return;
        ActiveRun run = ACTIVE.get(playerId);
        MutableMetrics metrics = run == null ? null : run.metrics();
        if (metrics != null) action.accept(metrics);
    }

    public record Snapshot(
            long tickSamples, long tickNanos, long maxTickNanos,
            long processedUnits, long slices, long timeBudgetExhausted, long unitBudgetExhausted,
            Map<TaskType, Integer> maxActive, Map<TaskType, Integer> maxWaiting,
            int bufferItems, int bufferStacks, int maxBufferItems, int maxBufferStacks,
            long bufferAgeTicks, long maxBufferAgeTicks, long bufferFallbacks,
            long pageBuilds, long pageSends, long endpointRebuilds, long endpointReuses) {
        private static final Snapshot EMPTY = new Snapshot(
                0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public long averageTickNanos() {
            return tickSamples == 0 ? 0L : tickNanos / tickSamples;
        }
    }

    record BufferSample(int items, int stacks, long ageTicks) {
        private static final BufferSample EMPTY = new BufferSample(0, 0, 0);
    }

    public record FinishResult(boolean accepted, Snapshot snapshot) {
        private static final FinishResult REJECTED = new FinishResult(false, Snapshot.EMPTY);
    }

    private record ActiveRun(String runId, String task, MutableMetrics metrics) {
    }

    private static final class MutableMetrics {
        long tickSamples;
        long tickNanos;
        long maxTickNanos;
        long processedUnits;
        long slices;
        long timeBudgetExhausted;
        long unitBudgetExhausted;
        final EnumMap<TaskType, Integer> maxActive = new EnumMap<>(TaskType.class);
        final EnumMap<TaskType, Integer> maxWaiting = new EnumMap<>(TaskType.class);
        int bufferItems;
        int bufferStacks;
        int maxBufferItems;
        int maxBufferStacks;
        long bufferAgeTicks;
        long maxBufferAgeTicks;
        long bufferFallbacks;
        long pageBuilds;
        long pageSends;
        long endpointRebuilds;
        long endpointReuses;

        Snapshot snapshot() {
            return new Snapshot(tickSamples, tickNanos, maxTickNanos,
                    processedUnits, slices, timeBudgetExhausted, unitBudgetExhausted,
                    Map.copyOf(maxActive), Map.copyOf(maxWaiting),
                    bufferItems, bufferStacks, maxBufferItems, maxBufferStacks,
                    bufferAgeTicks, maxBufferAgeTicks, bufferFallbacks,
                    pageBuilds, pageSends, endpointRebuilds, endpointReuses);
        }
    }
}
