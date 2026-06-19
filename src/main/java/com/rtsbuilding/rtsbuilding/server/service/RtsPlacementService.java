package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 放置服务——管理方块放置、批量放置和方块旋转。
 *
 * <p>职责范围：
 * <ul>
 *   <li>选中方块放置</li>
 *   <li>批量方块放置入队</li>
 *   <li>方块旋转</li>
 * </ul>
 *
 * <p>从 Phase 3 开始，工作流启动委托给 {@link PipelineRegistry}，
 * 此类仅负责参数转换和管道调度。</p>
 */
public final class RtsPlacementService {

    private RtsPlacementService() {
    }

    /**
     * 放置选中方块——通过 PLACE_SINGLE / QUICK_BUILD 管道执行。
     */
    public static void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand) {
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);

        boolean selectedStoragePlacement = itemId != null && !itemId.isBlank();
        boolean workflowPlacement = !forceEmptyHand && (quickBuild || selectedStoragePlacement);
        if (player != null && session != null && workflowPlacement) {
            Map<String, Object> args = new HashMap<>();
            args.put(PlacementExecutePipe.ARG_CLICKED_POSITIONS,
                    clickedPos == null ? List.of() : List.of(clickedPos));
            args.put(PlacementExecutePipe.ARG_FACE, face);
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_X, hitOffsetX);
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Y, hitOffsetY);
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Z, hitOffsetZ);
            args.put(PlacementExecutePipe.ARG_ROTATE_STEPS, (int) rotateSteps);
            args.put(PlacementExecutePipe.ARG_FORCE_PLACE, forcePlace);
            args.put(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED, skipIfOccupied);
            args.put(PlacementExecutePipe.ARG_ITEM_ID, itemId);
            args.put(PlacementExecutePipe.ARG_ITEM_PROTOTYPE, itemPrototype);
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_X, rayOriginX);
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Y, rayOriginY);
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Z, rayOriginZ);
            args.put(PlacementExecutePipe.ARG_RAY_DIR_X, rayDirX);
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Y, rayDirY);
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Z, rayDirZ);
            args.put(PlacementExecutePipe.ARG_QUICK_BUILD, quickBuild);
            args.put(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND, false);
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), 1);

            PipelineRegistry.execute(quickBuild ? RtsWorkflowType.QUICK_BUILD : RtsWorkflowType.PLACE_SINGLE,
                    new PlaceContext(player, args));
            return;
        }

        // Fallback: forceEmptyHand or no session — enqueue without workflow
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                session,
                clickedPos == null ? List.of() : List.of(clickedPos),
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                forceEmptyHand,
                true,
                -1);
    }

    /**
     * 批量方块放置入队——通过 PLACE_BATCH 管道执行。
     */
    public static void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);

        if (player != null && session != null && clickedPositions != null && !clickedPositions.isEmpty()) {
            // Pre-filter positions to match what RtsPlacementBatch.enqueuePlaceBatch would do,
            // so we can pass the correct totalBlocks to WorkflowStartPipe.
            List<BlockPos> sanitized = new ArrayList<>(Math.min(clickedPositions.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
            for (BlockPos pos : clickedPositions) {
                if (pos != null && RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                    sanitized.add(pos.immutable());
                    if (sanitized.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                        break;
                    }
                }
            }

            Map<String, Object> args = new HashMap<>();
            args.put(PlacementExecutePipe.ARG_CLICKED_POSITIONS, sanitized);
            args.put(PlacementExecutePipe.ARG_FACE, face);
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_X, hitOffsetX);
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Y, hitOffsetY);
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Z, hitOffsetZ);
            args.put(PlacementExecutePipe.ARG_ROTATE_STEPS, (int) rotateSteps);
            args.put(PlacementExecutePipe.ARG_FORCE_PLACE, forcePlace);
            args.put(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED, skipIfOccupied);
            args.put(PlacementExecutePipe.ARG_ITEM_ID, itemId == null ? "" : itemId);
            args.put(PlacementExecutePipe.ARG_ITEM_PROTOTYPE, itemPrototype);
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_X, rayOriginX);
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Y, rayOriginY);
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Z, rayOriginZ);
            args.put(PlacementExecutePipe.ARG_RAY_DIR_X, rayDirX);
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Y, rayDirY);
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Z, rayDirZ);
            args.put(PlacementExecutePipe.ARG_QUICK_BUILD, false);
            args.put(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND, false);
            args.put(PlacementExecutePipe.ARG_SEND_REMOTE_HINT, true);
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), sanitized.size());

            PipelineRegistry.execute(RtsWorkflowType.PLACE_BATCH,
                    new PlaceContext(player, args));
            return;
        }

        // Fallback: no session or empty positions — enqueue without workflow
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                session,
                clickedPositions,
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                true,
                false,
                false,
                -1);

        // 即使无会话，也尝试恢复挂起作业
        if (player != null) {
            RtsPendingPlacementService.tryResumeAfterStorageChange(player);
        }
    }

    /**
     * 提交挂起放置作业——尝试恢复所有因物品不足而暂停的放置任务。
     */
    public static int submitPendingPlacement(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return 0;
        }
        int count = RtsPendingPlacementService.resumeAllPendingJobs(player, session);
        if (count > 0) {
            player.displayClientMessage(
                    Component.literal("Resumed " + count + " pending placement job(s)."), true);
        } else {
            player.displayClientMessage(
                    Component.literal("No pending placements can be resumed — insufficient items."), true);
        }
        return count;
    }

    /**
     * 旋转已放置的方块。
     */
    public static void rotateBlock(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }

    // =========================================================================
    //  Placement Progress Queries
    // =========================================================================

    /**
     * 获取当前批量范围放置的总方块数。
     */
    public static int getPlaceBatchTotalBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::totalBlocks)
                .sum();
    }

    /**
     * 获取当前批量范围放置的已放置方块数量。
     */
    public static int getPlaceBatchCompletedBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::completedBlocks)
                .sum();
    }

    /**
     * 获取当前批量范围放置的未放置方块数。
     */
    public static int getPlaceBatchRemainingBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::remainingBlocks)
                .sum();
    }

    /**
     * 获取当前批量范围放置的方块类型（物品 ID）。
     */
    public static String getPlaceBatchItemId(ServerPlayer player) {
        if (player == null) return "";
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) return "";
        if (!session.placement.placeBatchJobs.isEmpty()) {
            return session.placement.placeBatchJobs.peekFirst().itemId();
        }
        if (!session.placement.pendingJobs.isEmpty()) {
            return session.placement.pendingJobs.peekFirst().itemId();
        }
        return "";
    }
}
