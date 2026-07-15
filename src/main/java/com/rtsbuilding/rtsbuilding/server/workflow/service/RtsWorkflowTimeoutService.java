package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 在服务端 Tick 线程上定期清理长期没有更新的工作流。
 *
 * <p>本服务只拥有“何时扫描”的调度状态，不创建线程，也不直接发送网络包。
 * 超时删除、事件分发和脏标记都在调用 {@link #tick(MinecraftServer, long)} 的线程完成；
 * 实际工作流同步由 Tick 末的副作用提交器合并执行。</p>
 *
 * <p>{@link #start(Duration, Duration)} 只启用并配置服务。没有显式启动时，
 * {@code tick} 保持 O(1) 空操作，以保持旧版本中该选配服务未启用时的行为。</p>
 */
public final class RtsWorkflowTimeoutService {

    private static final long MILLIS_PER_TICK = 50L;

    private final Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers;
    private final RtsWorkflowEventBus eventBus;
    private final BiConsumer<UUID, ResourceKey<Level>> workflowDirtySink;
    private final Predicate<MinecraftServer> serverThreadCheck;
    private final WorkflowTimeoutDeadline deadline = new WorkflowTimeoutDeadline();

    private long maxIdleMillis;
    private boolean enabled;

    /**
     * 创建生产环境使用的超时服务。
     *
     * @param slotManagers 工作流槽位映射；服务只在到期扫描时遍历
     * @param eventBus     工作流生命周期事件总线
     */
    public RtsWorkflowTimeoutService(
            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers,
            RtsWorkflowEventBus eventBus) {
        this(
                slotManagers,
                eventBus,
                (playerId, dimension) -> RtsEffectAccumulator.INSTANCE.markWorkflow(playerId, dimension),
                server -> server != null && server.isSameThread());
    }

    /**
     * 测试注入入口。它只替换 Tick 末脏标记接收者与线程判定，不改变生产调度逻辑。
     */
    RtsWorkflowTimeoutService(
            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> slotManagers,
            RtsWorkflowEventBus eventBus,
            BiConsumer<UUID, ResourceKey<Level>> workflowDirtySink,
            Predicate<MinecraftServer> serverThreadCheck) {
        this.slotManagers = Objects.requireNonNull(slotManagers, "slotManagers");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.workflowDirtySink = Objects.requireNonNull(workflowDirtySink, "workflowDirtySink");
        this.serverThreadCheck = Objects.requireNonNull(serverThreadCheck, "serverThreadCheck");
    }

    /**
     * 启用定期扫描。重复启动保持幂等，不会覆盖正在运行的配置。
     *
     * @param checkInterval 扫描间隔，向上取整到完整 Tick
     * @param maxIdleTime   工作流允许的最大无更新时间
     */
    public void start(Duration checkInterval, Duration maxIdleTime) {
        Objects.requireNonNull(checkInterval, "checkInterval");
        Objects.requireNonNull(maxIdleTime, "maxIdleTime");
        if (enabled) {
            return;
        }

        long intervalMillis = checkInterval.toMillis();
        long idleMillis = maxIdleTime.toMillis();
        if (intervalMillis <= 0L) {
            throw new IllegalArgumentException("checkInterval must be positive");
        }
        if (idleMillis < 0L) {
            throw new IllegalArgumentException("maxIdleTime must not be negative");
        }

        long intervalTicks = Math.max(1L, ((intervalMillis - 1L) / MILLIS_PER_TICK) + 1L);
        deadline.start(intervalTicks);
        maxIdleMillis = idleMillis;
        enabled = true;

        RtsbuildingMod.LOGGER.info(
                "[WorkflowTimeout] Started on server tick thread (interval={}, maxIdle={})",
                checkInterval, maxIdleTime);
    }

    /** 停止扫描并清除 deadline；重复停止保持幂等。 */
    public void stop() {
        enabled = false;
        maxIdleMillis = 0L;
        deadline.stop();
    }

    /**
     * 在服务端 Tick 中推进超时调度。
     *
     * @param server   当前 Minecraft 服务端，用于强制校验调用线程
     * @param gameTime 单调递增的服务端游戏 Tick 时间
     */
    public void tick(MinecraftServer server, long gameTime) {
        if (!enabled) {
            return;
        }
        if (!serverThreadCheck.test(server)) {
            throw new IllegalStateException("Workflow timeout mutation must run on the server thread");
        }
        if (!deadline.shouldRun(gameTime)) {
            return;
        }
        scanAndCleanup();
    }

    /**
     * 执行一次到期扫描。调用者已经通过主线程校验，因此这里可以安全地修改工作流状态、
     * 同步分发事件，并把网络刷新合并到 Tick 末。
     */
    private void scanAndCleanup() {
        int total = 0;

        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry
                : slotManagers.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimensions = playerEntry.getValue();

            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimensionEntry
                    : dimensions.entrySet()) {
                RtsWorkflowSlotManager slots = dimensionEntry.getValue();
                List<Integer> staleIds = slots.removeStaleEntries(maxIdleMillis);

                if (!staleIds.isEmpty()) {
                    for (int staleId : staleIds) {
                        eventBus.fire(new WorkflowEvent(
                                WorkflowEventType.TIMEOUT, playerId, staleId, null));
                        total++;
                    }
                    workflowDirtySink.accept(playerId, dimensionEntry.getKey());
                }
            }

            // 单线程 Tick 内直接收口空维度；不会为每 Tick 构造快照集合。
            dimensions.entrySet().removeIf(
                    entry -> entry.getValue().occupiedCount() == 0 && entry.getValue().size() == 0);
        }

        slotManagers.values().removeIf(Map::isEmpty);

        if (total > 0) {
            RtsbuildingMod.LOGGER.info("[WorkflowTimeout] Cleaned up {} stale workflow(s)", total);
        }
    }
}

/**
 * 纯 Tick deadline。首次看到 game time 时只建立基准，之后使用 fixed-delay 语义：
 * 即使服务端跳过多个 Tick，也只补做一次扫描，避免恢复后形成扫描风暴。
 */
final class WorkflowTimeoutDeadline {
    private long intervalTicks;
    private long nextRunGameTime;
    private boolean initialized;

    void start(long intervalTicks) {
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException("intervalTicks must be positive");
        }
        this.intervalTicks = intervalTicks;
        this.nextRunGameTime = 0L;
        this.initialized = false;
    }

    void stop() {
        intervalTicks = 0L;
        nextRunGameTime = 0L;
        initialized = false;
    }

    boolean shouldRun(long gameTime) {
        if (intervalTicks <= 0L) {
            return false;
        }
        if (!initialized) {
            nextRunGameTime = saturatingAdd(gameTime, intervalTicks);
            initialized = true;
            return false;
        }
        if (gameTime < nextRunGameTime) {
            return false;
        }
        nextRunGameTime = saturatingAdd(gameTime, intervalTicks);
        return true;
    }

    private static long saturatingAdd(long value, long delta) {
        if (value > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return value + delta;
    }
}
