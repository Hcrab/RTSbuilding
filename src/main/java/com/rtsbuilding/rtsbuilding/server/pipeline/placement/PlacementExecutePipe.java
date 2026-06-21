package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 将放置请求入队到 Forge 1.20.1 现有批处理队列。
 *
 * <p>这里是 pipeline 与旧放置服务之间的薄桥：pipeline 负责启动 workflow
 * 并提供 entryId，旧队列继续负责逐 tick 真正放置方块。</p>
 */
public final class PlacementExecutePipe implements PipelinePipe<PlaceContext> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPos>> ARG_CLICKED_POSITIONS =
            new TypedKey("clickedPositions", (Class) List.class);
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

    @Override
    public PipelineResult execute(PlaceContext ctx) {
        PlaceContext pctx = PlaceContext.require(ctx);
        RtsStorageSession session = pctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        ServerPlayer player = pctx.player();
        int workflowEntryId = pctx.hasWorkflowEntryId() ? pctx.getWorkflowEntryId() : -1;
        boolean enqueued = RtsPlacementBatch.enqueuePlaceBatch(player, session,
                pctx.getClickedPositions(),
                pctx.getFace(),
                pctx.getHitOffsetX(),
                pctx.getHitOffsetY(),
                pctx.getHitOffsetZ(),
                pctx.getRotateSteps(),
                pctx.isForcePlace(),
                pctx.isSkipIfOccupied(),
                pctx.getItemId(),
                pctx.getItemPrototype(),
                pctx.getRayOriginX(),
                pctx.getRayOriginY(),
                pctx.getRayOriginZ(),
                pctx.getRayDirX(),
                pctx.getRayDirY(),
                pctx.getRayDirZ(),
                pctx.isQuickBuild(),
                pctx.isForceEmptyHand(),
                pctx.isSendRemoteHint(),
                workflowEntryId);

        if (!enqueued && workflowEntryId >= 0) {
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> token.complete());
        }
        return PipelineResult.success();
    }
}
