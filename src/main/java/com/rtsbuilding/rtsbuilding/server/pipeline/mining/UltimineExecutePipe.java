package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.NetworkSyncPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsUltimineProcessor;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/**
 * 批量挖掘 pipeline 的执行阶段。
 *
 * <p>Forge 1.20.1 仍保留现有的逐 tick 挖掘状态机；这个 pipe 只把
 * main 已有的工作流/校验/工具借用编排接入 Forge。这样玩家看到的连锁挖掘、
 * 区域挖掘和区域破坏手感保持不变，但维护者看到的入口结构与 1.21.1 对齐。</p>
 */
public record UltimineExecutePipe(RtsWorkflowType type) implements PipelinePipe<MiningContext> {
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
            new TypedKey("positions", (Class) List.class);

    public static final TypedKey<RtsToolLease> KEY_TOOL_LEASE = ToolBorrowPipe.KEY_TOOL_LEASE;
    public static final TypedKey<Boolean> KEY_SELECTED_TOOL_REQUESTED = ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED;
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    public UltimineExecutePipe {
        if (type != RtsWorkflowType.ULTIMINE
                && type != RtsWorkflowType.AREA_MINE
                && type != RtsWorkflowType.AREA_DESTROY) {
            throw new IllegalArgumentException("UltimineExecutePipe only supports batch mining workflow types");
        }
    }

    @Override
    public PipelineResult execute(MiningContext ctx) {
        RtsStorageSession session = ctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        RtsToolLease lease = ctx.getToolLease();
        if (lease == null) {
            lease = RtsToolLease.empty();
        }
        int workflowEntryId = ctx.getWorkflowEntryId();
        RtsUltimineProcessor.PipelineBatchStartResult result;

        switch (type) {
            case ULTIMINE -> result = startUltimine(ctx, session, lease, workflowEntryId);
            case AREA_MINE -> result = startAreaMine(ctx, session, lease, workflowEntryId);
            case AREA_DESTROY -> result = startAreaDestroy(ctx, session, lease, workflowEntryId);
            default -> throw new IllegalStateException("Unexpected workflow type: " + type);
        }

        ctx.setData(NetworkSyncPipe.ARG_TOTAL_BLOCKS, result.targetCount());
        ctx.setData(NetworkSyncPipe.ARG_PROCESSED_BLOCKS, 0);
        updateWorkflowSlot(ctx, result);
        return PipelineResult.success();
    }

    private RtsUltimineProcessor.PipelineBatchStartResult startUltimine(
            MiningContext ctx, RtsStorageSession session, RtsToolLease lease, int workflowEntryId) {
        BlockPos pos = Objects.requireNonNull(ctx.getPos(), "ULTIMINE missing seed position");
        Direction face = ctx.getFace();
        int requestedLimit = valueOrDefault(ARG_REQUESTED_LIMIT, ctx, RtsMiningValidator.ULTIMINE_MAX_BLOCKS);
        byte mode = valueOrDefault(ARG_MODE, ctx, (byte) 0);
        return RtsUltimineProcessor.startUltimineFromPipeline(
                ctx.player(), session, pos, face,
                (byte) RtsMiningValidator.clampHotbarSlot(ctx.getToolSlot()),
                lease, ctx.isSelectedToolRequested(), requestedLimit, mode,
                ctx.isToolProtectionEnabled(), workflowEntryId);
    }

    private RtsUltimineProcessor.PipelineBatchStartResult startAreaMine(
            MiningContext ctx, RtsStorageSession session, RtsToolLease lease, int workflowEntryId) {
        int minX = Objects.requireNonNull(ctx.getArg(ARG_MIN_X), "AREA_MINE missing minX");
        int maxX = Objects.requireNonNull(ctx.getArg(ARG_MAX_X), "AREA_MINE missing maxX");
        int minY = Objects.requireNonNull(ctx.getArg(ARG_MIN_Y), "AREA_MINE missing minY");
        int maxY = Objects.requireNonNull(ctx.getArg(ARG_MAX_Y), "AREA_MINE missing maxY");
        int minZ = Objects.requireNonNull(ctx.getArg(ARG_MIN_Z), "AREA_MINE missing minZ");
        int maxZ = Objects.requireNonNull(ctx.getArg(ARG_MAX_Z), "AREA_MINE missing maxZ");
        byte shapeType = valueOrDefault(ARG_SHAPE_TYPE, ctx, (byte) 0);
        byte fillType = valueOrDefault(ARG_FILL_TYPE, ctx, (byte) 0);
        return RtsUltimineProcessor.areaMineFromPipeline(
                ctx.player(), session,
                minX, maxX, minY, maxY, minZ, maxZ,
                (byte) RtsMiningValidator.clampHotbarSlot(ctx.getToolSlot()),
                lease, ctx.isSelectedToolRequested(), shapeType, fillType,
                ctx.isToolProtectionEnabled(), workflowEntryId);
    }

    private RtsUltimineProcessor.PipelineBatchStartResult startAreaDestroy(
            MiningContext ctx, RtsStorageSession session, RtsToolLease lease, int workflowEntryId) {
        List<BlockPos> positions = ctx.getArg(ARG_POSITIONS);
        return RtsUltimineProcessor.areaDestroyFromPipeline(
                ctx.player(), session, positions,
                (byte) RtsMiningValidator.clampHotbarSlot(ctx.getToolSlot()),
                lease, ctx.isSelectedToolRequested(),
                ctx.isToolProtectionEnabled(), workflowEntryId);
    }

    private void updateWorkflowSlot(MiningContext ctx, RtsUltimineProcessor.PipelineBatchStartResult result) {
        if (!ctx.hasWorkflowEntryId()) {
            return;
        }
        RtsWorkflowEngine.getInstance()
                .from(ctx.player(), ctx.getWorkflowEntryId())
                .ifPresent(token -> {
                    token.setTotalBlocks(result.targetCount());
                    if (!result.asyncActive()) {
                        token.complete();
                    }
                });
    }

    private static int valueOrDefault(TypedKey<Integer> key, MiningContext ctx, int fallback) {
        Integer value = ctx.getArg(key);
        return value != null ? value : fallback;
    }

    private static byte valueOrDefault(TypedKey<Byte> key, MiningContext ctx, byte fallback) {
        Byte value = ctx.getArg(key);
        return value != null ? value : fallback;
    }
}
