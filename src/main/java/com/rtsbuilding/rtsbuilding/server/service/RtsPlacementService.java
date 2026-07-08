package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 放置服务——管理方块放置、批量放置和方块旋转??
 *
 * <p>职责范围??
 * <ul>
 *   <li>选中方块放置</li>
 *   <li>批量方块放置入队</li>
 *   <li>方块旋转</li>
 * </ul>
 */
public final class RtsPlacementService {

    public static final RtsPlacementService INSTANCE = new RtsPlacementService();

    private RtsPlacementService() {
    }

    /**
     * 放置选中方块??
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
     * 批量方块放置入队??
     */
    public static void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
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
                false);
    }

    /**
     * 旋转已放置的方块??
     */
    public static void rotateBlock(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        if (!RtsClaimProtectionService.canInteractBlock(
                player, pos, Direction.UP, InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            return;
        }
        RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }
}
