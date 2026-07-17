package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsUltimineProcessor;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/**
 * 执行批处理挖掘操作——连锁挖掘、区域挖掘或区域破坏。
 *
 * <p>此 Pipe 是 {@link RtsWorkflowType#ULTIMINE}、
 * {@link RtsWorkflowType#AREA_MINE} 和 {@link RtsWorkflowType#AREA_DESTROY}
 * 的"执行"阶段。
 * 它从管道上下文（由上游 {@link ToolBorrowPipe} 和 {@link WorkflowStartPipe} 设置）
 * 读取工具租约和工作流条目，将它们存储在玩家会话中，
 * 并将实际工作委托给 {@link RtsUltimineProcessor}。</p>
 *
 * <p>预期的上下文参数（按操作类型变化）：</p>
 *
 * <p><b>ULTIMINE:</b></p>
 * <ul>
 *   <li>{@code "pos"} —— {@link BlockPos} 种子位置</li>
 *   <li>{@code "face"} —— {@link Direction} 挖掘面（可选）</li>
 *   <li>{@code "requestedLimit"} —— {@code int} 最大挖掘方块数</li>
 *   <li>{@code "mode"} —— {@code byte} 连锁挖掘模式</li>
 * </ul>
 *
 * <p><b>AREA_MINE:</b></p>
 * <ul>
 *   <li>{@code "minX"}, {@code "maxX"}, {@code "minY"}, {@code "maxY"},
 *       {@code "minZ"}, {@code "maxZ"} —— {@code int} 区域边界</li>
 *   <li>{@code "shapeType"} —— {@code byte} 形状类型</li>
 *   <li>{@code "fillType"} —— {@code byte} 填充类型</li>
 * </ul>
 *
 * <p><b>AREA_DESTROY:</b></p>
 * <ul>
 *   <li>{@code "positions"} —— {@code List<BlockPos>} 要破坏的显式位置列表</li>
 * </ul>
 */
public record UltimineExecutePipe(RtsWorkflowType type) implements PipelinePipe<MiningContext> {

    public static final TypedKey<BlockPos> ARG_POS =
            new TypedKey<>("pos", BlockPos.class);
    public static final TypedKey<Direction> ARG_FACE =
            new TypedKey<>("face", Direction.class);
    public static final TypedKey<Integer> ARG_REQUESTED_LIMIT =
            new TypedKey<>("requestedLimit", Integer.class);
    public static final TypedKey<Byte> ARG_MODE =
            new TypedKey<>("mode", Byte.class);
    public static final TypedKey<Integer> ARG_MIN_X =
            new TypedKey<>("minX", Integer.class);
    public static final TypedKey<Integer> ARG_MAX_X =
            new TypedKey<>("maxX", Integer.class);
    public static final TypedKey<Integer> ARG_MIN_Y =
            new TypedKey<>("minY", Integer.class);
    public static final TypedKey<Integer> ARG_MAX_Y =
            new TypedKey<>("maxY", Integer.class);
    public static final TypedKey<Integer> ARG_MIN_Z =
            new TypedKey<>("minZ", Integer.class);
    public static final TypedKey<Integer> ARG_MAX_Z =
            new TypedKey<>("maxZ", Integer.class);
    public static final TypedKey<Byte> ARG_SHAPE_TYPE =
            new TypedKey<>("shapeType", Byte.class);
    public static final TypedKey<Byte> ARG_FILL_TYPE =
            new TypedKey<>("fillType", Byte.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPos>> ARG_POSITIONS =
            new TypedKey<>("positions", (Class) List.class);
    public static final TypedKey<Boolean> ARG_TOOL_PROTECTION_ENABLED =
            new TypedKey<>("toolProtectionEnabled", Boolean.class);

    public static final TypedKey<RtsToolLease> KEY_TOOL_LEASE = ToolBorrowPipe.KEY_TOOL_LEASE;
    public static final TypedKey<Boolean> KEY_SELECTED_TOOL_REQUESTED = ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED;
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    /**
     * 紧凑构造函数，验证批处理挖掘类型。
     *
     * @param type 批处理挖掘类型（{@link RtsWorkflowType#ULTIMINE}、
     *             {@link RtsWorkflowType#AREA_MINE} 或
     *             {@link RtsWorkflowType#AREA_DESTROY}）
     */
    public UltimineExecutePipe {
        if (type != RtsWorkflowType.ULTIMINE
                && type != RtsWorkflowType.AREA_MINE
                && type != RtsWorkflowType.AREA_DESTROY) {
            throw new IllegalArgumentException("UltimineExecutePipe only supports ULTIMINE, AREA_MINE, and AREA_DESTROY");
        }
    }

    @Override
    public PipelineResult execute(MiningContext ctx) {
        MiningContext mctx = ctx;
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context — SessionValidatePipe must run first");
        }

        // ── 从上游 ToolBorrowPipe 将工具租约存储到会话 ─────
        if (mctx.hasToolLease()) {
            session.mining.miningToolLease = mctx.getToolLease();
        }
        if (mctx.isSelectedToolRequested()) {
            session.mining.miningSelectedToolRequested = true;
        }

        byte toolSlot = (byte) RtsMiningValidator.clampHotbarSlot(mctx.getToolSlot());
        boolean toolProtectionEnabled = mctx.isToolProtectionEnabled();

        // 在 workflow-entry-ID 追踪之前解析队列模式
        boolean queueMode = Boolean.TRUE.equals(mctx.getData(StopPreviousPipe.KEY_QUEUE_MODE));

        RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] Executing {} for player={}, queueMode={}, toolSlot={}",
                type, mctx.player().getGameProfile().name(), queueMode, toolSlot);

        // ── 在会话的 RtsMiningState 中存储工作流条目 ID ──
        //    在队列模式下，条目存储在 MiningJob 记录中
        //    并由 activateNextJob() 恢复；我们绝不能覆盖当前
        //    活跃的条目，否则 finalizeMiningOperation 将完成错误的
        //    工作流条目，导致排队的作业被强制停止。
        if (!queueMode && mctx.hasWorkflowEntryId()) {
            session.mining.workflowEntryId = mctx.getWorkflowEntryId();
        }

        switch (type) {
            case ULTIMINE: {
                BlockPos pos = mctx.getPos();
                Direction face = mctx.getFace();
                int requestedLimit = mctx.hasArg(ARG_REQUESTED_LIMIT)
                        ? Objects.requireNonNull(mctx.getArg(ARG_REQUESTED_LIMIT), "ULTIMINE missing required arg: requestedLimit") : Integer.MAX_VALUE;
                byte mode = mctx.hasArg(ARG_MODE) ? Objects.requireNonNull(mctx.getArg(ARG_MODE), "ULTIMINE missing required arg: mode") : (byte) 0;

                if (queueMode) {
                    int queuedCount = RtsUltimineProcessor.queueStartUltimine(
                            mctx.player(), session, pos, face,
                            toolSlot, requestedLimit, mode, toolProtectionEnabled,
                            mctx.getWorkflowEntryId());
                    RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] ULTIMINE queued {} blocks for {}",
                            queuedCount, mctx.player().getGameProfile().name());
                    if (queuedCount > 0 && mctx.hasWorkflowEntryId()) {
                        RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                                .ifPresent(token -> token.setTotalBlocks(queuedCount));
                    }
                    if (queuedCount > 0) {
                        markTaskSubmitted(mctx, false);
                    } else {
                        completeWithoutTask(mctx, session);
                    }
                    return PipelineResult.success();
                }

                boolean started = RtsUltimineProcessor.startUltimine(mctx.player(), session, pos, face,
                        toolSlot, requestedLimit, mode, toolProtectionEnabled);
                if (!started) {
                    completeWithoutTask(mctx, session);
                    return PipelineResult.success();
                }
                break;
            }
            case AREA_MINE: {
                int minX = Objects.requireNonNull(mctx.getArg(ARG_MIN_X), "AREA_MINE missing required arg: minX");
                int maxX = Objects.requireNonNull(mctx.getArg(ARG_MAX_X), "AREA_MINE missing required arg: maxX");
                int minY = Objects.requireNonNull(mctx.getArg(ARG_MIN_Y), "AREA_MINE missing required arg: minY");
                int maxY = Objects.requireNonNull(mctx.getArg(ARG_MAX_Y), "AREA_MINE missing required arg: maxY");
                int minZ = Objects.requireNonNull(mctx.getArg(ARG_MIN_Z), "AREA_MINE missing required arg: minZ");
                int maxZ = Objects.requireNonNull(mctx.getArg(ARG_MAX_Z), "AREA_MINE missing required arg: maxZ");
                byte shapeType = mctx.hasArg(ARG_SHAPE_TYPE) ? Objects.requireNonNull(mctx.getArg(ARG_SHAPE_TYPE), "AREA_MINE missing required arg: shapeType") : (byte) 0;
                byte fillType = mctx.hasArg(ARG_FILL_TYPE) ? Objects.requireNonNull(mctx.getArg(ARG_FILL_TYPE), "AREA_MINE missing required arg: fillType") : (byte) 0;

                if (queueMode) {
                    int queuedCount = RtsUltimineProcessor.queueAreaMine(
                            mctx.player(), session,
                            minX, maxX, minY, maxY, minZ, maxZ,
                            toolSlot, shapeType, fillType, toolProtectionEnabled,
                            mctx.getWorkflowEntryId());
                    RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] AREA_MINE queued {} blocks for {}",
                            queuedCount, mctx.player().getGameProfile().name());
                    if (queuedCount > 0 && mctx.hasWorkflowEntryId()) {
                        RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                                .ifPresent(token -> token.setTotalBlocks(queuedCount));
                    }
                    if (queuedCount > 0) {
                        markTaskSubmitted(mctx, false);
                    } else {
                        completeWithoutTask(mctx, session);
                    }
                    return PipelineResult.success();
                }

                boolean started = RtsUltimineProcessor.areaMine(mctx.player(), session,
                        minX, maxX, minY, maxY, minZ, maxZ,
                        toolSlot, shapeType, fillType, toolProtectionEnabled);
                if (!started) {
                    completeWithoutTask(mctx, session);
                    return PipelineResult.success();
                }
                break;
            }
            case AREA_DESTROY: {
                List<BlockPos> positions = mctx.getArg(ARG_POSITIONS);
                int requestSize = positions != null ? positions.size() : 0;
                RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] AREA_DESTROY enqueuing {} positions for {}",
                        requestSize, mctx.player().getGameProfile().name());

                boolean enqueued = RtsDestructionBatch.enqueueDestroyBatch(
                        mctx.player(), session, positions,
                        (byte) RtsMiningValidator.clampHotbarSlot(mctx.getToolSlot()),
                        mctx.isToolProtectionEnabled(),
                        mctx.hasWorkflowEntryId() ? mctx.getWorkflowEntryId() : -1);

                if (enqueued && mctx.hasWorkflowEntryId()) {
                    int totalTargets = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                            .workflowTaskTotalUnits(mctx.player(), mctx.getWorkflowEntryId());
                    RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                            .ifPresent(token -> token.setTotalBlocks(totalTargets));
                }

                // 如果入队被静默跳过（无有效位置、队列已满等），
                // 完成工作流条目以防止槽泄漏
                if (enqueued) {
                    markTaskSubmitted(mctx, !queueMode);
                } else {
                    completeWithoutTask(mctx, session);
                }
                return PipelineResult.success();
            }
            default:
                throw new IllegalStateException("Unexpected type: " + type);
        }

        // ── Switch 后的逻辑（仅限非队列模式） ───────────────

        // 为下游管道在上下文中存储批处理信息
        // 在已知目标数后更新工作流总方块数
        if (mctx.hasWorkflowEntryId()) {
            int totalTargets = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                    .workflowTaskTotalUnits(mctx.player(), mctx.getWorkflowEntryId());
            RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                    .ifPresent(token -> token.setTotalBlocks(totalTargets));
        }

        // 只有领域作业已经成功建立后才移交；下游失败时 rollback 会取消作业并只归还一次。
        markTaskSubmitted(mctx, true);

        return PipelineResult.success();
    }

    private static void markTaskSubmitted(MiningContext ctx, boolean ownsPipelineLease) {
        ctx.setData(ToolBorrowPipe.KEY_ASYNC_TASK_SUBMITTED, true);
        if (ownsPipelineLease && ctx.hasToolLease()) {
            ctx.setData(ToolBorrowPipe.KEY_TOOL_LEASE_TRANSFERRED, true);
        }
    }

    /** 无有效目标或创造模式已同步完成时，立即关闭工作流并归还本次借到的工具。 */
    private static void completeWithoutTask(MiningContext ctx, RtsStorageSession session) {
        if (ctx.hasWorkflowEntryId()) {
            RtsWorkflowEngine.getInstance().from(ctx.player(), ctx.getWorkflowEntryId())
                    .ifPresent(token -> token.complete());
            if (session.mining.workflowEntryId == ctx.getWorkflowEntryId()) {
                session.mining.workflowEntryId = -1;
            }
        }
        if (!ctx.hasToolLease()) {
            return;
        }
        RtsToolLease lease = ctx.getToolLease();
        if (lease != null && !lease.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(ctx.player(), session, lease);
        }
        session.mining.miningToolLease = RtsToolLease.empty();
        session.mining.miningSelectedToolRequested = false;
        ctx.setData(ToolBorrowPipe.KEY_TOOL_LEASE_RETURNED, true);
    }
}
