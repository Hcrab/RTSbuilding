package com.rtsbuilding.rtsbuilding.common.shape.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 区域形状生成输入参数 —— 封装了形状生成器所需的所有几何参数。
 * <p>
 * 携带锚点位置、两个对角点（定义形状的覆盖范围）、
 * 高度偏移（用于 BOX / WALL 等 3D 形状）、点击面方向和放置面方向。
 *
 * @param start        锚点 / 第一个角坐标
 * @param end          第二个角坐标（定义形状的延伸范围）
 * @param heightOffset 相对于基准平面的垂直偏移（2D 形状为 0）
 * @param clickedFace  玩家点击的面的方向
 * @param placementFace 放置方块时的贴附面方向
 */
public record AreaShapeInput(
        BlockPos start,
        BlockPos end,
        int heightOffset,
        Direction clickedFace,
        Direction placementFace) {

    /**
     * 创建一个仅包含两个角点的最小输入（默认使用 UP 方向）。
     *
     * @param start 第一个角坐标
     * @param end   第二个角坐标
     * @return AreaShapeInput 实例
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.UP, Direction.UP);
    }

    /**
     * 创建破坏操作专用的输入（无需放置面方向）。
     *
     * @param start 第一个角坐标
     * @param end   第二个角坐标
     * @return AreaShapeInput 实例
     */
    public static AreaShapeInput destroy(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.DOWN, Direction.DOWN);
    }

    /**
     * 创建包含所有参数的完整输入。
     *
     * @param start         第一个角坐标
     * @param end           第二个角坐标
     * @param heightOffset  高度偏移量
     * @param clickedFace   点击面方向
     * @param placementFace 放置面方向
     * @return AreaShapeInput 实例
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end, int heightOffset,
                                     Direction clickedFace, Direction placementFace) {
        return new AreaShapeInput(start, end, heightOffset, clickedFace, placementFace);
    }
}
