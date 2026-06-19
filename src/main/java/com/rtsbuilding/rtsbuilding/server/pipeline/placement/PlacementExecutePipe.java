package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.*;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 将放置批处理作业（单方块、批处理或快速构建）入队到
 * 玩家的放置作业队列。
 *
 * <p>此 Pipe 是 {@link
 * RtsWorkflowType#PLACE_SINGLE}、
 * {@link RtsWorkflowType#PLACE_BATCH}
 * 和 {@link RtsWorkflowType#QUICK_BUILD}
 * 的"执行"阶段。
 * 它从管道上下文读取会话和工作流条目 ID，
 * 并委托给 {@link RtsPlacementBatch#enqueuePlaceBatch}。</p>
 *
 * <p>此 Pipe 声明为 {@link PipelinePipe}{@code <PlaceContext>}，
 * 因此编译器强制类型安全——
 * 通过 {@link PipelineRegistry#placementPipeline(RtsWorkflowType)} 使用。
 * 调用 {@link PlaceContext#require(PipelineContext)} 进行安全转换。</p>
 */
public final class PlacementExecutePipe implements PipelinePipe<PlaceContext> {

    // ── 参数键常量（由 PlaceContext 访问器使用） ──

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPos>> ARG_CLICKED_POSITIONS =
            new TypedKey<>("clickedPositions", (Class) List.class);
    public static final TypedKey<Direction> ARG_FACE =
            new TypedKey<>("face", Direction.class);
    public static final TypedKey<Double> ARG_HIT_OFFSET_X =
            new TypedKey<>("hitOffsetX", Double.class);
    public static final TypedKey<Double> ARG_HIT_OFFSET_Y =
            new TypedKey<>("hitOffsetY", Double.class);
    public static final TypedKey<Double> ARG_HIT_OFFSET_Z =
            new TypedKey<>("hitOffsetZ", Double.class);
    public static final TypedKey<Integer> ARG_ROTATE_STEPS =
            new TypedKey<>("rotateSteps", Integer.class);
    public static final TypedKey<Boolean> ARG_FORCE_PLACE =
            new TypedKey<>("forcePlace", Boolean.class);
    public static final TypedKey<Boolean> ARG_SKIP_IF_OCCUPIED =
            new TypedKey<>("skipIfOccupied", Boolean.class);
    public static final TypedKey<String> ARG_ITEM_ID =
            new TypedKey<>("itemId", String.class);
    public static final TypedKey<ItemStack> ARG_ITEM_PROTOTYPE =
            new TypedKey<>("itemPrototype", ItemStack.class);
    public static final TypedKey<Double> ARG_RAY_ORIGIN_X =
            new TypedKey<>("rayOriginX", Double.class);
    public static final TypedKey<Double> ARG_RAY_ORIGIN_Y =
            new TypedKey<>("rayOriginY", Double.class);
    public static final TypedKey<Double> ARG_RAY_ORIGIN_Z =
            new TypedKey<>("rayOriginZ", Double.class);
    public static final TypedKey<Double> ARG_RAY_DIR_X =
            new TypedKey<>("rayDirX", Double.class);
    public static final TypedKey<Double> ARG_RAY_DIR_Y =
            new TypedKey<>("rayDirY", Double.class);
    public static final TypedKey<Double> ARG_RAY_DIR_Z =
            new TypedKey<>("rayDirZ", Double.class);
    public static final TypedKey<Boolean> ARG_QUICK_BUILD =
            new TypedKey<>("quickBuild", Boolean.class);
    public static final TypedKey<Boolean> ARG_FORCE_EMPTY_HAND =
            new TypedKey<>("forceEmptyHand", Boolean.class);
    public static final TypedKey<Boolean> ARG_SEND_REMOTE_HINT =
            new TypedKey<>("sendRemoteHint", Boolean.class);

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PlaceContext ctx) {
        PlaceContext pctx = ctx;
        RtsStorageSession session = pctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context — SessionValidatePipe must run first");
        }

        ServerPlayer player = pctx.player();

        // ── 通过类型安全访问器读取放置参数 ──
        List<BlockPos> clickedPositions = pctx.getClickedPositions();
        Direction face = pctx.getFace();
        double hitOffsetX = pctx.getHitOffsetX();
        double hitOffsetY = pctx.getHitOffsetY();
        double hitOffsetZ = pctx.getHitOffsetZ();
        // 从参数（不可变输入）中读取，而不是从 data（可变共享状态）中读取
        byte rotateSteps = pctx.getRotateSteps();
        boolean forcePlace = pctx.isForcePlace();
        boolean skipIfOccupied = pctx.isSkipIfOccupied();
        String itemId = pctx.getItemId();
        ItemStack itemPrototype = pctx.getItemPrototype();
        double rayOriginX = pctx.getRayOriginX();
        double rayOriginY = pctx.getRayOriginY();
        double rayOriginZ = pctx.getRayOriginZ();
        double rayDirX = pctx.getRayDirX();
        double rayDirY = pctx.getRayDirY();
        double rayDirZ = pctx.getRayDirZ();
        boolean quickBuild = pctx.isQuickBuild();
        boolean forceEmptyHand = pctx.isForceEmptyHand();
        boolean sendRemoteHint = pctx.isSendRemoteHint();

        int workflowEntryId = pctx.hasWorkflowEntryId()
                ? pctx.getWorkflowEntryId() : -1;

        boolean enqueued = RtsPlacementBatch.enqueuePlaceBatch(player, session, clickedPositions,
                face, hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps,
                forcePlace, skipIfOccupied, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ,
                quickBuild, forceEmptyHand, sendRemoteHint,
                workflowEntryId);

        // ── 如果入队被静默跳过（无有效位置、队列已满等），
        //    完成工作流条目以防止槽泄漏 ──────────────
        if (!enqueued && workflowEntryId >= 0) {
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> token.complete());
            return PipelineResult.success();
        }

        return PipelineResult.success();
    }
}
