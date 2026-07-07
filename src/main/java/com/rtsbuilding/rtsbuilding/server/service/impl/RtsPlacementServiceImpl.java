package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.PlacementService;
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
 * {@link PlacementService} 的默认实现——处理 RTS 模式下的远程方块放置操作。
 *
 * <p>该实现类通过 {@link com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry}
 * 执行放置流程：
 * <ul>
 *   <li>单方块放置（{@code PLACE_SINGLE}）</li>
 *   <li>快速建造（{@code QUICK_BUILD}）</li>
 *   <li>批量放置（{@code PLACE_BATCH}）</li>
 * </ul>
 * 同时管理挂起放置作业的恢复、方块旋转和进度查询。
 * 当工作流不可用时回退到直接入队（{@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch}）。
 */
public final class RtsPlacementServiceImpl implements PlacementService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face,
                              double hitX, double hitY, double hitZ, byte rotateSteps,
                              boolean forcePlace, boolean skipIfOccupied, String itemId,
                              ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                              double rayDirX, double rayDirY, double rayDirZ,
                              boolean quickBuild, boolean forceEmptyHand) {
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);

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

        // 回退：forceEmptyHand 或无会话 — 入队但不经过工作流
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

    @Override
    public void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
                                  double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
                                  boolean forcePlace, boolean skipIfOccupied, String itemId,
                                  ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                                  double rayDirX, double rayDirY, double rayDirZ) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);

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

        // 回退：无会话或空位置 — 入队但不经过工作流
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

        if (player != null) {
            RtsPendingPlacementService.tryResumeAfterStorageChange(player);
        }
    }

    @Override
    public int submitPendingPlacement(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
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

    @Override
    public void rotateBlock(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)) {
            return;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        if (!RtsClaimProtectionService.canInteractBlock(
                player, pos, Direction.UP, net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            return;
        }
        RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }

    @Override
    public int getPlaceBatchTotalBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::totalBlocks)
                .sum();
    }

    @Override
    public int getPlaceBatchCompletedBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::completedBlocks)
                .sum();
    }

    @Override
    public int getPlaceBatchRemainingBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::remainingBlocks)
                .sum();
    }

    @Override
    public String getPlaceBatchItemId(ServerPlayer player) {
        if (player == null) return "";
        RtsStorageSession session = registry.session().getIfPresent(player);
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
