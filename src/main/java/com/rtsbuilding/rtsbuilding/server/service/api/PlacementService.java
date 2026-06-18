package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 放置服务接口——管理方块放置、批量放置和方块旋转。
 */
public interface PlacementService {

    /** 放置选中方块。 */
    void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face,
                       double hitX, double hitY, double hitZ, byte rotateSteps,
                       boolean forcePlace, boolean skipIfOccupied, String itemId,
                       ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                       double rayDirX, double rayDirY, double rayDirZ,
                       boolean quickBuild, boolean forceEmptyHand);

    /** 批量方块放置入队。 */
    void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
                           double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
                           boolean forcePlace, boolean skipIfOccupied, String itemId,
                           ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                           double rayDirX, double rayDirY, double rayDirZ);

    /** 提交挂起放置作业。 */
    int submitPendingPlacement(ServerPlayer player);

    /** 旋转已放置的方块。 */
    void rotateBlock(ServerPlayer player, BlockPos pos);

    /** 获取当前批量范围放置的总方块数。 */
    int getPlaceBatchTotalBlocks(ServerPlayer player);

    /** 获取当前批量范围放置的已放置方块数量。 */
    int getPlaceBatchCompletedBlocks(ServerPlayer player);

    /** 获取当前批量范围放置的未放置方块数。 */
    int getPlaceBatchRemainingBlocks(ServerPlayer player);

    /** 获取当前批量范围放置的方块类型（物品 ID）。 */
    String getPlaceBatchItemId(ServerPlayer player);
}
