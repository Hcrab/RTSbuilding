package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                player == null ? null : RtsSessionService.getIfPresent(player),
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
                true);
    }

    /**
     * 批量方块放置入队??
     */
    public static void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                player == null ? null : RtsSessionService.getIfPresent(player),
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
        RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }
}
