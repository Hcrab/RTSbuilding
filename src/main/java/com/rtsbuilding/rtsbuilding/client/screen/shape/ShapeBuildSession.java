package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 形状构建会话数据。
 * <p>
 * 记录当前正在进行的形状构建交互的完整状态，
 * 包括形状类型、朝向、锚点、当前阶段和高度偏移。
 *
 * @param shape              形状类型
 * @param planeFace          形状所在的基准面朝向
 * @param placementFace      形状放置的目标面朝向
 * @param pointA             第一个锚点（起点）
 * @param pointB             第二个锚点（终点）
 * @param phase              当前构建阶段
 * @param boxHeightOffset    立方体高度偏移（仅立方体使用）
 * @param boxHeightMouseBaseY 立方体高度拖拽的鼠标基准 Y 坐标
 */
public record ShapeBuildSession(
        ClientRtsController.BuildShape shape,
        Direction planeFace,
        Direction placementFace,
        BlockPos pointA,
        BlockPos pointB,
        ShapeBuildPhase phase,
        int boxHeightOffset,
        double boxHeightMouseBaseY) {
}
