package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
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
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

import java.util.ArrayList;
import java.util.List;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

/**
 * Placement service — manages block placement, batch placement, and block rotation.
 *
 * <p>Scope of responsibilities:
 * <ul>
 *   <li>Selected block placement</li>
 *   <li>Batch block placement queuing</li>
 *   <li>Block rotation</li>
 * </ul>
 *
 * <p>Starting from Phase 3, workflow initiation is delegated to {@link PipelineRegistry},
 * this class only handles parameter conversion and pipeline scheduling.</p>
 */
public final class RtsPlacementService {

    private RtsPlacementService() {
    }

    /**
     * Places a selected block — executed via PLACE_SINGLE / QUICK_BUILD pipeline.
     */
    public static void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand) {
        if (RtsCameraManager.isActive(player)) {
            var session = RtsServer.get().session().getIfPresent(player);
            if (session == null || session.mode != BuilderMode.BUILD) return;
        }
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        RtsStorageSession session = player == null ? null : RtsServer.get().session().getIfPresent(player);

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
     * Queues batch block placement — executed via PLACE_BATCH pipeline.
     */
    public static void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        RtsStorageSession session = player == null ? null : RtsServer.get().session().getIfPresent(player);

        if (player != null && session != null && clickedPositions != null && !clickedPositions.isEmpty()) {
            List<BlockPos> sanitized = new ArrayList<>(Math.min(clickedPositions.size(), NetworkConstants.MAX_POSITIONS));
            for (BlockPos pos : clickedPositions) {
                if (pos != null && RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                    sanitized.add(pos.immutable());
                    if (sanitized.size() >= NetworkConstants.MAX_POSITIONS) {
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

        // Even without a session, attempt to resume pending jobs
        if (player != null) {
            RtsPendingPlacementService.tryResumeAfterStorageChange(player);
        }
    }

    /**
     * Submits pending placement jobs — attempts to resume all placement tasks paused due to insufficient items.
     */
    public static int submitPendingPlacement(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
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
     * Rotates a placed block.
     */
    public static void rotateBlock(ServerPlayer player, BlockPos pos) {
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }

    // =========================================================================
    //  Placement Progress Queries
    // =========================================================================

    /**
     * Gets the total block count for the current batch area placement.
     */
    public static int getPlaceBatchTotalBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::totalBlocks)
                .sum();
    }

    /**
     * Gets the number of placed blocks for the current batch area placement.
     */
    public static int getPlaceBatchCompletedBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::completedBlocks)
                .sum();
    }

    /**
     * Gets the number of unplaced blocks for the current batch area placement.
     */
    public static int getPlaceBatchRemainingBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::remainingBlocks)
                .sum();
    }

    /**
     * Gets the block type (item ID) for the current batch area placement.
     */
    public static String getPlaceBatchItemId(ServerPlayer player) {
        if (player == null) return "";
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
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
