package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 蓝图任务的执行载荷，拥有准备阶段和放置阶段之间的唯一运行时游标。
 *
 * <p>它不执行世界、材料或 Capability 副作用；这里只把原先一次性完成的计划计算、
 * 排序和队列生成拆成预算内的小步。真正放置仍由服务端主线程上的蓝图执行器完成。</p>
 */
public final class BlueprintTaskPayload implements TaskPayload {
    private enum PreparationStage { PLANS, ORDER, QUEUE, READY }

    private final BlueprintContext context;
    private final ResourceKey<Level> dimension;
    private final LinkedList<Integer> restoredRemaining;
    private final List<PlacementPlan> plans = new ArrayList<>();
    private PriorityQueue<Integer> orderedIndices;
    private PreparationStage stage = PreparationStage.PLANS;
    private int planCursor;
    private int orderCursor;
    private long lastCheckpointTick = Long.MIN_VALUE;
    private final NoProgressCycleTracker placementCycle = new NoProgressCycleTracker();

    public BlueprintTaskPayload(BlueprintContext context, @Nullable LinkedList<Integer> restoredRemaining) {
        this(context, restoredRemaining, context.player().serverLevel().dimension());
    }

    public BlueprintTaskPayload(BlueprintContext context, @Nullable LinkedList<Integer> restoredRemaining,
            ResourceKey<Level> dimension) {
        this.context = context;
        this.dimension = dimension;
        this.restoredRemaining = restoredRemaining == null ? null : new LinkedList<>(restoredRemaining);
    }

    public BlueprintContext context() {
        return context;
    }

    public ServerPlayer player() {
        return context.player();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public int workflowEntryId() {
        Integer entryId = context.getData(
                com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return entryId == null ? -1 : entryId;
    }

    public boolean ready() {
        return stage == PreparationStage.READY;
    }

    public void beginPlacementCycle(int candidates) {
        placementCycle.beginIfIdle(candidates);
    }

    public boolean recordDeferredPlacement() {
        return placementCycle.deferredOne();
    }

    public void recordPlacementProgress(int unresolvedCandidates) {
        placementCycle.progressed(unresolvedCandidates);
    }

    public void resetPlacementCycle() {
        placementCycle.reset();
    }

    /** 正常进度最多每秒写一次快照；等待和终态由调用方强制写。 */
    public boolean shouldCheckpoint(boolean force) {
        long tick = player().serverLevel().getGameTime();
        if (!force && lastCheckpointTick != Long.MIN_VALUE && tick - lastCheckpointTick < 20L) {
            return false;
        }
        lastCheckpointTick = tick;
        return true;
    }

    /**
     * 在当前调度片内推进准备工作，返回实际消费的 work unit 数。
     */
    public int prepare(TaskBudget budget) {
        int processed = 0;
        RtsBlueprint blueprint = context.getBlueprint();
        int total = blueprint.blockCount();
        while (stage != PreparationStage.READY
                && processed < budget.maxUnits()
                && budget.hasTime()) {
            switch (stage) {
                case PLANS -> {
                    if (planCursor >= total) {
                        finishPlans();
                        continue;
                    }
                    plans.add(BlockPlacementPlanner.computeOne(
                            blueprint.blocks().get(planCursor++),
                            context.getAnchor(), context.getData(BlueprintContext.KEY_CENTER_OFFSET),
                            context.getYRotationSteps(), context.getXRotationSteps(),
                            context.getZRotationSteps()));
                    processed++;
                }
                case ORDER -> {
                    if (orderCursor >= total) {
                        stage = PreparationStage.QUEUE;
                        continue;
                    }
                    orderedIndices.add(orderCursor++);
                    processed++;
                }
                case QUEUE -> {
                    if (orderedIndices.isEmpty()) {
                        context.setRemainingQueue(new LinkedList<>());
                        context.setPreparing(false);
                        stage = PreparationStage.READY;
                        continue;
                    }
                    context.getRemainingQueue().addLast(orderedIndices.remove());
                    processed++;
                }
                case READY -> {
                    // while 条件会结束；保留分支以满足穷尽 switch。
                }
            }
        }
        return processed;
    }

    private void finishPlans() {
        context.setPlacementPlans(Collections.unmodifiableList(new ArrayList<>(plans)));
        if (restoredRemaining != null) {
            // 恢复任务保留崩溃前的环形队列顺序，避免改变缺料重试语义。
            context.setRemainingQueue(new LinkedList<>(restoredRemaining));
            context.setPreparing(false);
            stage = PreparationStage.READY;
            return;
        }

        BlockPos center = context.getAnchor().offset(context.getData(BlueprintContext.KEY_CENTER_OFFSET));
        Comparator<Integer> comparator = Comparator.<Integer, Integer>comparing(i -> {
            PlacementPlan plan = plans.get(i);
            return plan == null ? Integer.MAX_VALUE : plan.target().getY();
        }).thenComparingDouble(i -> {
            PlacementPlan plan = plans.get(i);
            return plan == null ? Double.MAX_VALUE : plan.target().distSqr(center);
        }).thenComparingInt(Integer::intValue);
        orderedIndices = new PriorityQueue<>(Math.max(1, plans.size()), comparator);
        context.setRemainingQueue(new LinkedList<>());
        stage = PreparationStage.ORDER;
    }
}
