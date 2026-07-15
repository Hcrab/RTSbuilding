package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 蓝图放置操作的强类型管道上下文。
 *
 * <p>提供对蓝图特定参数和共享数据的类型安全访问器，
 * 消除了整个蓝图放置管道实现中的强制转换。</p>
 *
 * <p>属于蓝图管道（BLUEPRINT_BUILD）的 Pipe 应在
 * {@link PipelinePipe#execute(PipelineContext)} 开头
 * 调用 {@link #require(PipelineContext)}:</p>
 * <pre>{@code
 * BlueprintContext bctx = BlueprintContext.require(ctx);
 * RtsBlueprint blueprint = bctx.getBlueprint();
 * BlockPos anchor = bctx.getAnchor();
 * }</pre>
 */
public class BlueprintContext extends PipelineContext {

    // ──────────────────────────────────────────────────────────────
    //  参数键
    // ──────────────────────────────────────────────────────────────

    public static final TypedKey<RtsBlueprint> ARG_BLUEPRINT =
            new TypedKey<>("blueprint", RtsBlueprint.class);
    public static final TypedKey<BlockPos> ARG_ANCHOR =
            new TypedKey<>("anchor", BlockPos.class);
    public static final TypedKey<Integer> ARG_Y_ROTATION_STEPS =
            new TypedKey<>("yRotationSteps", Integer.class);
    public static final TypedKey<Integer> ARG_X_ROTATION_STEPS =
            new TypedKey<>("xRotationSteps", Integer.class);
    public static final TypedKey<Integer> ARG_Z_ROTATION_STEPS =
            new TypedKey<>("zRotationSteps", Integer.class);

    // ──────────────────────────────────────────────────────────────
    //  共享数据键（进度追踪）
    // ──────────────────────────────────────────────────────────────

    /** 预计算的放置计划列表，与 blocks 索引一一对应。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPlacementPlanner.PlacementPlan>> KEY_PLACEMENT_PLANS =
            new TypedKey("blueprintPlacementPlans", (Class) List.class);

    /** 环形队列——仍未放置的方块索引。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<LinkedList<Integer>> KEY_REMAINING_QUEUE =
            new TypedKey("blueprintRemainingQueue", (Class) LinkedList.class);

    public static final TypedKey<Integer> KEY_PLACED_COUNT =
            new TypedKey<>("blueprintPlacedCount", Integer.class);
    public static final TypedKey<Integer> KEY_SKIPPED_MISSING =
            new TypedKey<>("blueprintSkippedMissing", Integer.class);
    public static final TypedKey<Integer> KEY_SKIPPED_UNSUPPORTED =
            new TypedKey<>("blueprintSkippedUnsupported", Integer.class);
    public static final TypedKey<Integer> KEY_SKIPPED_MISSING_BLOCKS =
            new TypedKey<>("blueprintSkippedMissingBlocks", Integer.class);
    public static final TypedKey<Integer> KEY_SKIPPED_BLOCKED =
            new TypedKey<>("blueprintSkippedBlocked", Integer.class);

    /** 计划与有序队列仍在由统一任务预算分片构建。 */
    public static final TypedKey<Boolean> KEY_PREPARING =
            new TypedKey<>("blueprintPreparing", Boolean.class);

    /** 蓝图任务创建时的来源维度，不能随玩家切维漂移。 */
    public static final TypedKey<ResourceKey<Level>> KEY_SOURCE_DIMENSION =
            new TypedKey<>("blueprintSourceDimension", castResourceKeyClass());

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<ResourceKey<Level>> castResourceKeyClass() {
        return (Class) ResourceKey.class;
    }

    /** 旋转中心偏移量，由 {@code BlueprintExecutePipe} 计算并存放。 */
    public static final TypedKey<BlockPos> KEY_CENTER_OFFSET =
            new TypedKey<>("blueprintCenterOffset", BlockPos.class);

    // ──────────────────────────────────────────────────────────────
    //  构造
    // ──────────────────────────────────────────────────────────────

    private BlueprintContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    /**
     * 创建一个新的 {@link Builder}，用于构建 {@link BlueprintContext}。
     */
    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    /**
     * 安全地将 {@link PipelineContext} 转换为 {@link BlueprintContext}。
     *
     * @param ctx 要转换的管道上下文
     * @return 相同的上下文，类型化为 {@code BlueprintContext}
     * @throws IllegalArgumentException 如果 {@code ctx} 不是 {@code BlueprintContext} 实例
     */
    public static BlueprintContext require(PipelineContext ctx) {
        if (ctx instanceof BlueprintContext pc) {
            return pc;
        }
        throw new IllegalArgumentException(
                "Expected BlueprintContext but got " + ctx.getClass().getSimpleName()
                + ". This pipe requires a blueprint pipeline (BLUEPRINT_BUILD). "
                + "Did you register it in the wrong pipeline?");
    }

    // ──────────────────────────────────────────────────────────────
    //  蓝图参数访问器
    // ──────────────────────────────────────────────────────────────

    /** 返回要放置的蓝图。 */
    public RtsBlueprint getBlueprint() {
        return getArg(ARG_BLUEPRINT);
    }

    /** 返回蓝图的锚点坐标。 */
    public BlockPos getAnchor() {
        return getArg(ARG_ANCHOR);
    }

    /** 返回 Y 轴旋转步数。 */
    public int getYRotationSteps() {
        Integer val = getArg(ARG_Y_ROTATION_STEPS);
        return val != null ? val : 0;
    }

    /** 返回 X 轴旋转步数。 */
    public int getXRotationSteps() {
        Integer val = getArg(ARG_X_ROTATION_STEPS);
        return val != null ? val : 0;
    }

    /** 返回 Z 轴旋转步数。 */
    public int getZRotationSteps() {
        Integer val = getArg(ARG_Z_ROTATION_STEPS);
        return val != null ? val : 0;
    }

    // ──────────────────────────────────────────────────────────────
    //  预计算放置计划访问器
    // ──────────────────────────────────────────────────────────────

    /** 返回预计算的放置计划列表（来自 data，retainOnly 保护）。 */
    @SuppressWarnings("unchecked")
    public List<BlockPlacementPlanner.PlacementPlan> getPlacementPlans() {
        return (List<BlockPlacementPlanner.PlacementPlan>) getData(KEY_PLACEMENT_PLANS);
    }

    /** 设置预计算的放置计划列表。 */
    public void setPlacementPlans(List<BlockPlacementPlanner.PlacementPlan> plans) {
        setData(KEY_PLACEMENT_PLANS, plans);
    }

    // ──────────────────────────────────────────────────────────────
    //  环形队列访问器
    // ──────────────────────────────────────────────────────────────

    /** 返回仍未放置的方块索引队列（环形队列，缺材料的排到队尾）。 */
    @SuppressWarnings("unchecked")
    public LinkedList<Integer> getRemainingQueue() {
        return (LinkedList<Integer>) getData(KEY_REMAINING_QUEUE);
    }

    /** 设置仍未放置的方块索引队列。 */
    public void setRemainingQueue(LinkedList<Integer> queue) {
        setData(KEY_REMAINING_QUEUE, queue);
    }

    // ──────────────────────────────────────────────────────────────
    //  共享数据访问器（进度）
    // ──────────────────────────────────────────────────────────────

    /** 返回已放置的方块数量。 */
    public int getPlacedCount() {
        Integer val = getData(KEY_PLACED_COUNT);
        return val != null ? val : 0;
    }

    /** 设置已放置的方块数量。 */
    public void setPlacedCount(int count) {
        setData(KEY_PLACED_COUNT, count);
    }

    /** 返回因缺少材料跳过的次数。 */
    public int getSkippedMissing() {
        Integer val = getData(KEY_SKIPPED_MISSING);
        return val != null ? val : 0;
    }

    /** 设置因缺少材料跳过的次数。 */
    public void setSkippedMissing(int count) {
        setData(KEY_SKIPPED_MISSING, count);
    }

    /** 返回因不支持的方块跳过的次数。 */
    public int getSkippedUnsupported() {
        Integer val = getData(KEY_SKIPPED_UNSUPPORTED);
        return val != null ? val : 0;
    }

    /** 设置因不支持的方块跳过的次数。 */
    public void setSkippedUnsupported(int count) {
        setData(KEY_SKIPPED_UNSUPPORTED, count);
    }

    /** 返回因缺失定义跳过的次数。 */
    public int getSkippedMissingBlocks() {
        Integer val = getData(KEY_SKIPPED_MISSING_BLOCKS);
        return val != null ? val : 0;
    }

    /** 设置因缺失定义跳过的次数。 */
    public void setSkippedMissingBlocks(int count) {
        setData(KEY_SKIPPED_MISSING_BLOCKS, count);
    }

    /** 返回因位置堵塞跳过的次数。 */
    public int getSkippedBlocked() {
        Integer val = getData(KEY_SKIPPED_BLOCKED);
        return val != null ? val : 0;
    }

    /** 设置因位置堵塞跳过的次数。 */
    public void setSkippedBlocked(int count) {
        setData(KEY_SKIPPED_BLOCKED, count);
    }

    public boolean isPreparing() {
        return Boolean.TRUE.equals(getData(KEY_PREPARING));
    }

    public void setPreparing(boolean preparing) {
        setData(KEY_PREPARING, preparing);
    }

    // ──────────────────────────────────────────────────────────────
    //  会话访问器
    // ──────────────────────────────────────────────────────────────

    /**
     * 返回来自 {@link SessionValidatePipe} 解析的存储会话。
     */
    @Nullable
    public RtsStorageSession getResolvedSession() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    // ──────────────────────────────────────────────────────────────
    //  构建器
    // ──────────────────────────────────────────────────────────────

    /**
     * {@link BlueprintContext} 的类型安全流式构建器。
     */
    public static final class Builder {
        private final ServerPlayer player;
        private final Map<String, Object> args = new HashMap<>();

        private Builder(ServerPlayer player) {
            this.player = player;
        }

        /** 要放置的蓝图。 */
        public Builder blueprint(RtsBlueprint blueprint) {
            args.put(ARG_BLUEPRINT.name(), blueprint);
            return this;
        }

        /** 蓝图的锚点坐标。 */
        public Builder anchor(BlockPos anchor) {
            args.put(ARG_ANCHOR.name(), anchor);
            return this;
        }

        /** Y 轴旋转步数。 */
        public Builder yRotationSteps(int steps) {
            args.put(ARG_Y_ROTATION_STEPS.name(), steps);
            return this;
        }

        /** X 轴旋转步数。 */
        public Builder xRotationSteps(int steps) {
            args.put(ARG_X_ROTATION_STEPS.name(), steps);
            return this;
        }

        /** Z 轴旋转步数。 */
        public Builder zRotationSteps(int steps) {
            args.put(ARG_Z_ROTATION_STEPS.name(), steps);
            return this;
        }

        /** 工作流的总方块数。 */
        public Builder totalBlocks(int total) {
            args.put(com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        /** 构建 {@link BlueprintContext}。 */
        public BlueprintContext build() {
            return new BlueprintContext(player, args);
        }
    }
}
