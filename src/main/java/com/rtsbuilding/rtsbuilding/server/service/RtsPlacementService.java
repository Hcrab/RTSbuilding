package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
        RtsStorageSession session = player == null ? null : ServiceRegistry.getInstance().session().getIfPresent(player);

        if (player != null && session != null && !forceEmptyHand) {
            PipelineRegistry.execute(quickBuild ? RtsWorkflowType.QUICK_BUILD : RtsWorkflowType.PLACE_SINGLE,
                    PlaceContext.builder(player)
                            .clickedPositions(clickedPos == null ? List.of() : List.of(clickedPos))
                            .face(face)
                            .hitOffsetX(hitOffsetX)
                            .hitOffsetY(hitOffsetY)
                            .hitOffsetZ(hitOffsetZ)
                            .rotateSteps(rotateSteps)
                            .forcePlace(forcePlace)
                            .skipIfOccupied(skipIfOccupied)
                            .itemId(itemId)
                            .itemPrototype(itemPrototype)
                            .rayOriginX(rayOriginX)
                            .rayOriginY(rayOriginY)
                            .rayOriginZ(rayOriginZ)
                            .rayDirX(rayDirX)
                            .rayDirY(rayDirY)
                            .rayDirZ(rayDirZ)
                            .quickBuild(quickBuild)
                            .forceEmptyHand(false)
                            .totalBlocks(1)
                            .build());
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
        RtsStorageSession session = player == null ? null : ServiceRegistry.getInstance().session().getIfPresent(player);

        if (player != null && session != null && clickedPositions != null && !clickedPositions.isEmpty()) {
            List<BlockPos> sanitized = new ArrayList<>(Math.min(clickedPositions.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
            for (BlockPos pos : clickedPositions) {
                if (pos != null && RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                    sanitized.add(pos.immutable());
                    if (sanitized.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                        break;
                    }
                }
            }

            PipelineRegistry.execute(RtsWorkflowType.PLACE_BATCH,
                    PlaceContext.builder(player)
                            .clickedPositions(sanitized)
                            .face(face)
                            .hitOffsetX(hitOffsetX)
                            .hitOffsetY(hitOffsetY)
                            .hitOffsetZ(hitOffsetZ)
                            .rotateSteps(rotateSteps)
                            .forcePlace(forcePlace)
                            .skipIfOccupied(skipIfOccupied)
                            .itemId(itemId == null ? "" : itemId)
                            .itemPrototype(itemPrototype)
                            .rayOriginX(rayOriginX)
                            .rayOriginY(rayOriginY)
                            .rayOriginZ(rayOriginZ)
                            .rayDirX(rayDirX)
                            .rayDirY(rayDirY)
                            .rayDirZ(rayDirZ)
                            .quickBuild(false)
                            .forceEmptyHand(false)
                            .sendRemoteHint(true)
                            .totalBlocks(sanitized.size())
                            .build());
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
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
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
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        if (!RtsClaimProtectionService.canInteractBlock(
                player, pos, Direction.UP, net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
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
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
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
