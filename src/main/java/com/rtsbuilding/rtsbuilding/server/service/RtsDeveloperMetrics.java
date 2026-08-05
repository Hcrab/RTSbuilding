package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskScheduler;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectCommitBarrier;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
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

    public static boolean begin(EntityPlayerMP player, String runId, String task) {
        return player != null && begin(player.getUniqueID(), runId, task);
    }

    static boolean begin(UUID playerId, String runId, String task) {
        if (playerId == null || runId == null || task == null) return false;
        ActiveRun requested = new ActiveRun(runId, task, new MutableMetrics());
        ActiveRun existing = ACTIVE.putIfAbsent(playerId, requested);
        return existing == null || (existing.runId().equals(runId) && existing.task().equals(task));
    }

    public static FinishResult finish(EntityPlayerMP player, String runId, String task) {
        return finish(player == null ? null : player.getUniqueID(), runId, task);
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
        for (Map.Entry<UUID, ActiveRun> entry : ACTIVE.entrySet()) {
            EntityPlayerMP player = com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getPlayerList(server).getPlayerByUUID(entry.getKey());
            if (player == null) continue;
            RtsTaskEngine.TaskDiagnostics tasks = RtsTaskEngine.INSTANCE.diagnostics(player.getUniqueID());
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session =
                    ServiceRegistry.getInstance().session().getIfPresent(player);
            BufferSample bufferSample = BufferSample.EMPTY;
            if (session != null) {
                com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState buffer =
                        session.miningDropBuffer;
                long age = buffer.firstQueuedGameTime < 0L ? 0L
                        : Math.max(0L, player.getServerForPlayer().getTotalWorldTime() - buffer.firstQueuedGameTime);
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

    public static void recordPageBuild(EntityPlayerMP player) { mutate(player, m -> m.pageBuilds++); }
    public static void recordPageSend(EntityPlayerMP player) { mutate(player, m -> m.pageSends++); }
    public static void recordEndpointRebuild(UUID playerId) { mutate(playerId, m -> m.endpointRebuilds++); }
    public static void recordEndpointReuse(UUID playerId) { mutate(playerId, m -> m.endpointReuses++); }
    public static void recordBufferFallback(EntityPlayerMP player) { mutate(player, m -> m.bufferFallbacks++); }
    public static void recordSessionSnapshot(EntityPlayerMP player) { mutate(player, m -> m.sessionSnapshots++); }
    public static void recordWorkflowSnapshot(EntityPlayerMP player) { mutate(player, m -> m.workflowSnapshots++); }
    public static void recordHistorySnapshot(EntityPlayerMP player) { mutate(player, m -> m.historySnapshots++); }
    public static void recordPluginSnapshot(EntityPlayerMP player) { mutate(player, m -> m.pluginSnapshots++); }
    public static void recordProgressionSnapshot(EntityPlayerMP player) { mutate(player, m -> m.progressionSnapshots++); }

    /** Effect Barrier 自身也是增量计数器，不扫描任务或副作用对象图。 */
    public static void recordEffectCommit(RtsEffectCommitBarrier.CommitReport report) {
        if (report == null || ACTIVE.isEmpty()) return;
        for (ActiveRun run : ACTIVE.values()) {
            MutableMetrics metrics = run.metrics();
            metrics.effectAttemptedTargets += Math.max(0, report.attemptedTargets());
            metrics.effectCommittedKinds += Math.max(0, report.committedKinds());
            metrics.effectRetryTargets += Math.max(0, report.retryTargets());
            metrics.effectDeferredTargets += Math.max(0, report.deferredTargets());
            metrics.effectFailedTargets += Math.max(0, report.failedTargets());
        }
    }

    static void recordPageBuild(UUID playerId) { mutate(playerId, m -> m.pageBuilds++); }
    static void recordPageSend(UUID playerId) { mutate(playerId, m -> m.pageSends++); }
    static void recordBufferFallback(UUID playerId) { mutate(playerId, m -> m.bufferFallbacks++); }
    static void recordSessionSnapshot(UUID playerId) { mutate(playerId, m -> m.sessionSnapshots++); }
    static void recordWorkflowSnapshot(UUID playerId) { mutate(playerId, m -> m.workflowSnapshots++); }
    static void recordHistorySnapshot(UUID playerId) { mutate(playerId, m -> m.historySnapshots++); }
    static void recordPluginSnapshot(UUID playerId) { mutate(playerId, m -> m.pluginSnapshots++); }
    static void recordProgressionSnapshot(UUID playerId) { mutate(playerId, m -> m.progressionSnapshots++); }

    private static void mutate(EntityPlayerMP player, Consumer<MutableMetrics> action) {
        if (player != null) mutate(player.getUniqueID(), action);
    }

    private static void mutate(UUID playerId, Consumer<MutableMetrics> action) {
        if (playerId == null) return;
        ActiveRun run = ACTIVE.get(playerId);
        MutableMetrics metrics = run == null ? null : run.metrics();
        if (metrics != null) action.accept(metrics);
    }

    public static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(
                0, 0, 0, 0, 0, 0, 0, Collections.<TaskType, Integer>emptyMap(),
                Collections.<TaskType, Integer>emptyMap(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        private final long tickSamples, tickNanos, maxTickNanos;
        private final long processedUnits, slices, timeBudgetExhausted, unitBudgetExhausted;
        private final Map<TaskType, Integer> maxActive, maxWaiting;
        private final int bufferItems, bufferStacks, maxBufferItems, maxBufferStacks;
        private final long bufferAgeTicks, maxBufferAgeTicks, bufferFallbacks;
        private final long pageBuilds, pageSends, endpointRebuilds, endpointReuses;
        private final long sessionSnapshots, workflowSnapshots, historySnapshots;
        private final long pluginSnapshots, progressionSnapshots;
        private final long effectAttemptedTargets, effectCommittedKinds, effectRetryTargets;
        private final long effectDeferredTargets, effectFailedTargets;

        public Snapshot(long tickSamples, long tickNanos, long maxTickNanos,
                long processedUnits, long slices, long timeBudgetExhausted, long unitBudgetExhausted,
                Map<TaskType, Integer> maxActive, Map<TaskType, Integer> maxWaiting,
                int bufferItems, int bufferStacks, int maxBufferItems, int maxBufferStacks,
                long bufferAgeTicks, long maxBufferAgeTicks, long bufferFallbacks,
                long pageBuilds, long pageSends, long endpointRebuilds, long endpointReuses,
                long sessionSnapshots, long workflowSnapshots, long historySnapshots,
                long pluginSnapshots, long progressionSnapshots,
                long effectAttemptedTargets, long effectCommittedKinds, long effectRetryTargets,
                long effectDeferredTargets, long effectFailedTargets) {
            this.tickSamples = tickSamples; this.tickNanos = tickNanos; this.maxTickNanos = maxTickNanos;
            this.processedUnits = processedUnits; this.slices = slices;
            this.timeBudgetExhausted = timeBudgetExhausted; this.unitBudgetExhausted = unitBudgetExhausted;
            this.maxActive = immutableTaskMap(maxActive); this.maxWaiting = immutableTaskMap(maxWaiting);
            this.bufferItems = bufferItems; this.bufferStacks = bufferStacks;
            this.maxBufferItems = maxBufferItems; this.maxBufferStacks = maxBufferStacks;
            this.bufferAgeTicks = bufferAgeTicks; this.maxBufferAgeTicks = maxBufferAgeTicks;
            this.bufferFallbacks = bufferFallbacks; this.pageBuilds = pageBuilds; this.pageSends = pageSends;
            this.endpointRebuilds = endpointRebuilds; this.endpointReuses = endpointReuses;
            this.sessionSnapshots = sessionSnapshots; this.workflowSnapshots = workflowSnapshots;
            this.historySnapshots = historySnapshots; this.pluginSnapshots = pluginSnapshots;
            this.progressionSnapshots = progressionSnapshots; this.effectAttemptedTargets = effectAttemptedTargets;
            this.effectCommittedKinds = effectCommittedKinds; this.effectRetryTargets = effectRetryTargets;
            this.effectDeferredTargets = effectDeferredTargets; this.effectFailedTargets = effectFailedTargets;
        }

        private static Map<TaskType, Integer> immutableTaskMap(Map<TaskType, Integer> source) {
            EnumMap<TaskType, Integer> copy = new EnumMap<TaskType, Integer>(TaskType.class);
            if (source != null) copy.putAll(source);
            return Collections.unmodifiableMap(copy);
        }

        public long tickSamples() { return tickSamples; }
        public long tickNanos() { return tickNanos; }
        public long maxTickNanos() { return maxTickNanos; }
        public long processedUnits() { return processedUnits; }
        public long slices() { return slices; }
        public long timeBudgetExhausted() { return timeBudgetExhausted; }
        public long unitBudgetExhausted() { return unitBudgetExhausted; }
        public Map<TaskType, Integer> maxActive() { return maxActive; }
        public Map<TaskType, Integer> maxWaiting() { return maxWaiting; }
        public int bufferItems() { return bufferItems; }
        public int bufferStacks() { return bufferStacks; }
        public int maxBufferItems() { return maxBufferItems; }
        public int maxBufferStacks() { return maxBufferStacks; }
        public long bufferAgeTicks() { return bufferAgeTicks; }
        public long maxBufferAgeTicks() { return maxBufferAgeTicks; }
        public long bufferFallbacks() { return bufferFallbacks; }
        public long pageBuilds() { return pageBuilds; }
        public long pageSends() { return pageSends; }
        public long endpointRebuilds() { return endpointRebuilds; }
        public long endpointReuses() { return endpointReuses; }
        public long sessionSnapshots() { return sessionSnapshots; }
        public long workflowSnapshots() { return workflowSnapshots; }
        public long historySnapshots() { return historySnapshots; }
        public long pluginSnapshots() { return pluginSnapshots; }
        public long progressionSnapshots() { return progressionSnapshots; }
        public long effectAttemptedTargets() { return effectAttemptedTargets; }
        public long effectCommittedKinds() { return effectCommittedKinds; }
        public long effectRetryTargets() { return effectRetryTargets; }
        public long effectDeferredTargets() { return effectDeferredTargets; }
        public long effectFailedTargets() { return effectFailedTargets; }

        public long averageTickNanos() {
            return tickSamples == 0 ? 0L : tickNanos / tickSamples;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot)) return false;
            Snapshot s = (Snapshot) other;
            return tickSamples == s.tickSamples && tickNanos == s.tickNanos && maxTickNanos == s.maxTickNanos
                    && processedUnits == s.processedUnits && slices == s.slices
                    && timeBudgetExhausted == s.timeBudgetExhausted && unitBudgetExhausted == s.unitBudgetExhausted
                    && bufferItems == s.bufferItems && bufferStacks == s.bufferStacks
                    && maxBufferItems == s.maxBufferItems && maxBufferStacks == s.maxBufferStacks
                    && bufferAgeTicks == s.bufferAgeTicks && maxBufferAgeTicks == s.maxBufferAgeTicks
                    && bufferFallbacks == s.bufferFallbacks && pageBuilds == s.pageBuilds && pageSends == s.pageSends
                    && endpointRebuilds == s.endpointRebuilds && endpointReuses == s.endpointReuses
                    && sessionSnapshots == s.sessionSnapshots && workflowSnapshots == s.workflowSnapshots
                    && historySnapshots == s.historySnapshots && pluginSnapshots == s.pluginSnapshots
                    && progressionSnapshots == s.progressionSnapshots
                    && effectAttemptedTargets == s.effectAttemptedTargets
                    && effectCommittedKinds == s.effectCommittedKinds && effectRetryTargets == s.effectRetryTargets
                    && effectDeferredTargets == s.effectDeferredTargets && effectFailedTargets == s.effectFailedTargets
                    && java.util.Objects.equals(maxActive, s.maxActive)
                    && java.util.Objects.equals(maxWaiting, s.maxWaiting);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(tickSamples, tickNanos, maxTickNanos, processedUnits, slices,
                    timeBudgetExhausted, unitBudgetExhausted, maxActive, maxWaiting, bufferItems, bufferStacks,
                    maxBufferItems, maxBufferStacks, bufferAgeTicks, maxBufferAgeTicks, bufferFallbacks,
                    pageBuilds, pageSends, endpointRebuilds, endpointReuses, sessionSnapshots, workflowSnapshots,
                    historySnapshots, pluginSnapshots, progressionSnapshots, effectAttemptedTargets,
                    effectCommittedKinds, effectRetryTargets, effectDeferredTargets, effectFailedTargets);
        }
    }

    static final class BufferSample {
        private static final BufferSample EMPTY = new BufferSample(0, 0, 0);
        private final int items;
        private final int stacks;
        private final long ageTicks;
        BufferSample(int items, int stacks, long ageTicks) {
            this.items = items; this.stacks = stacks; this.ageTicks = ageTicks;
        }
        int items() { return items; }
        int stacks() { return stacks; }
        long ageTicks() { return ageTicks; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BufferSample)) return false;
            BufferSample that = (BufferSample) other;
            return items == that.items && stacks == that.stacks && ageTicks == that.ageTicks;
        }
        @Override public int hashCode() { return java.util.Objects.hash(items, stacks, ageTicks); }
    }

    public static final class FinishResult {
        private static final FinishResult REJECTED = new FinishResult(false, Snapshot.EMPTY);
        private final boolean accepted;
        private final Snapshot snapshot;
        public FinishResult(boolean accepted, Snapshot snapshot) {
            this.accepted = accepted; this.snapshot = snapshot;
        }
        public boolean accepted() { return accepted; }
        public Snapshot snapshot() { return snapshot; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FinishResult)) return false;
            FinishResult that = (FinishResult) other;
            return accepted == that.accepted && java.util.Objects.equals(snapshot, that.snapshot);
        }
        @Override public int hashCode() { return java.util.Objects.hash(accepted, snapshot); }
    }

    private static final class ActiveRun {
        private final String runId;
        private final String task;
        private final MutableMetrics metrics;
        private ActiveRun(String runId, String task, MutableMetrics metrics) {
            this.runId = runId; this.task = task; this.metrics = metrics;
        }
        private String runId() { return runId; }
        private String task() { return task; }
        private MutableMetrics metrics() { return metrics; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ActiveRun)) return false;
            ActiveRun that = (ActiveRun) other;
            return java.util.Objects.equals(runId, that.runId)
                    && java.util.Objects.equals(task, that.task)
                    && java.util.Objects.equals(metrics, that.metrics);
        }
        @Override public int hashCode() { return java.util.Objects.hash(runId, task, metrics); }
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
        long sessionSnapshots;
        long workflowSnapshots;
        long historySnapshots;
        long pluginSnapshots;
        long progressionSnapshots;
        long effectAttemptedTargets;
        long effectCommittedKinds;
        long effectRetryTargets;
        long effectDeferredTargets;
        long effectFailedTargets;

        Snapshot snapshot() {
            return new Snapshot(tickSamples, tickNanos, maxTickNanos,
                    processedUnits, slices, timeBudgetExhausted, unitBudgetExhausted,
                    maxActive, maxWaiting,
                    bufferItems, bufferStacks, maxBufferItems, maxBufferStacks,
                    bufferAgeTicks, maxBufferAgeTicks, bufferFallbacks,
                    pageBuilds, pageSends, endpointRebuilds, endpointReuses,
                    sessionSnapshots, workflowSnapshots, historySnapshots,
                    pluginSnapshots, progressionSnapshots,
                    effectAttemptedTargets, effectCommittedKinds, effectRetryTargets,
                    effectDeferredTargets, effectFailedTargets);
        }
    }
}
