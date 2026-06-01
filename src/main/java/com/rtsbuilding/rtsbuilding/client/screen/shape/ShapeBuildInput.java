package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 形状构建输入数据。
 * <p>
 * 包含用户通过点击世界方块定义形状时所需的所有输入参数，
 * 包括形状类型、基准面、放置面、两个锚点位置和高度偏移。
 *
 * @param shape          形状类型
 * @param planeFace      形状所在的基准面朝向
 * @param placementFace  形状放置的目标面朝向
 * @param pointA         第一个锚点（起点）
 * @param pointB         第二个锚点（终点）
 * @param boxHeightOffset 立方体高度偏移（仅 {@link ClientRtsController.BuildShape#BOX} 使用）
 */
public record ShapeBuildInput(
        ClientRtsController.BuildShape shape,
        Direction planeFace,
        Direction placementFace,
        BlockPos pointA,
        BlockPos pointB,
        int boxHeightOffset) {
}
