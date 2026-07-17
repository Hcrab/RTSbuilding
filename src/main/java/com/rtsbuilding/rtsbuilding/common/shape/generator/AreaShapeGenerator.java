package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域形状生成器抽象基类 —— 形状坐标生成的基础。
 * <p>
 * 每个具体子类负责为一种几何形状（长方体、墙体、直线、圆形等）
 * 根据 {@link AreaShapeInput} 生成一组 {@link BlockPos} 坐标。
 * 生成器产生的坐标相对于锚点位置，不涉及方块状态操作或物品提取。
 * 实际的方块操作由上层执行器 {@link com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor} 负责。
 */
public abstract class AreaShapeGenerator {

    /**
     * 生成该形状的方块位置列表。
     *
     * @param input    形状输入参数（锚点、边界、面等）
     * @param fillMode 填充策略（FILL / HOLLOW / SKELETON）
     * @return 有序的绝对世界坐标列表
     */
    public abstract List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode);

    /**
     * 返回该形状的可读名称 / 翻译键后缀。
     */
    public abstract String getName();

    // ======================================================================
    // 共享验证辅助方法
    // ======================================================================

    /**
     * 基础位置校验：检查建筑高度范围和世界交互权限。
     * <p>
     * {@link #validatePlacementPosition} 和 {@link #validateDestroyPosition}
     * 都以同样的两个检查开始——此方法统一了它们。
     *
     * @param level  世界
     * @param pos    目标位置
     * @param player 执行操作的玩家
     * @return true 如果位置在建筑高度内且玩家可与之交互
     */
    private static boolean validatePositionBase(Level level, BlockPos pos, Player player) {
        if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
            return false;
        }
        return level.mayInteract(player, pos);
    }

    /**
     * 验证方块位置是否可以放置方块。
     * <p>
     * 检查建筑高度、交互权限以及现有方块是否可被替换。
     *
     * @param level  世界
     * @param pos    目标位置
     * @param state  要放置的方块状态
     * @param player 执行操作的玩家
     * @return true 如果此处可以放置方块
     */
    public static boolean validatePlacementPosition(Level level, BlockPos pos, BlockState state, Player player) {
        if (!validatePositionBase(level, pos, player)) {
            return false;
        }
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        return level.getBlockState(pos).canBeReplaced();
    }

    /**
     * 验证方块位置是否可以被破坏。
     * <p>
     * 检查建筑高度、交互权限以及目标方块是否为空或不可破坏。
     *
     * @param level  世界
     * @param pos    目标位置
     * @param player 执行操作的玩家
     * @return true 如果此处的方块可以被破坏
     */
    public static boolean validateDestroyPosition(ServerLevel level, BlockPos pos, Player player) {
        if (!validatePositionBase(level, pos, player)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return true;
    }

    /**
     * 将形状偏移量限制在最大允许范围内（±64 格）。
     */
    protected static int clampOffset(int value) {
        int max = 64;
        return Math.max(-max, Math.min(max, value));
    }

    /**
     * 计算向量 (dx, dy, dz) 在指定轴上的投影（点积）。
     */
    protected static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return (dx * axis.getStepX()) + (dy * axis.getStepY()) + (dz * axis.getStepZ());
    }

    /**
     * 在两个位置之间生成一条直线（包含两端，Bresenham 风格）。
     */
    protected static List<BlockPos> generateLinePositions(BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        List<BlockPos> result = new ArrayList<>(steps + 1);
        if (steps <= 0) {
            result.add(start);
            return result;
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = start.getX() + (int) Math.round(dx * t);
            int y = start.getY() + (int) Math.round(dy * t);
            int z = start.getZ() + (int) Math.round(dz * t);
            result.add(new BlockPos(x, y, z));
        }
        return result;
    }

    /**
     * 根据点击面确定 2D 形状的两个平面轴。
     */
    protected static Direction[] resolvePlaneAxes(Direction face) {
        return switch (face.getAxis()) {
            case Y -> new Direction[]{Direction.EAST, Direction.SOUTH};
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Z -> new Direction[]{Direction.EAST, Direction.UP};
        };
    }

    /**
     * 沿两个平面轴从起点生成所有方块位置。
     */
    protected static List<BlockPos> buildPlanePositions(BlockPos start, Direction axisA, Direction axisB,
                                                         int aMin, int aMax, int bMin, int bMax) {
        List<BlockPos> result = new ArrayList<>();
        for (int a = aMin; a <= aMax; a++) {
            for (int b = bMin; b <= bMax; b++) {
                int dx = (axisA.getStepX() * a) + (axisB.getStepX() * b);
                int dy = (axisA.getStepY() * a) + (axisB.getStepY() * b);
                int dz = (axisA.getStepZ() * a) + (axisB.getStepZ() * b);
                result.add(start.offset(dx, dy, dz));
            }
        }
        return result;
    }

    /**
     * 过滤位置列表，仅保留边界位置（用于 HOLLOW / SKELETON 模式）。
     * <p>
     * 原理：如果一个方块在 X、Y、Z 中任一方向上的邻居不在集合中，则为边界方块。
     */
    protected static List<BlockPos> filterBoundary(List<BlockPos> full, int minY, int maxY) {
        java.util.Set<BlockPos> set = new java.util.HashSet<>(full);
        List<BlockPos> boundary = new ArrayList<>();
        for (BlockPos pos : full) {
            boolean xEdge = !set.contains(pos.east()) || !set.contains(pos.west());
            // 单层 2D 形状没有上下邻居，不能因此把内部格子误判成边界。
            boolean yEdge = minY != maxY && (!set.contains(pos.above()) || !set.contains(pos.below()));
            boolean zEdge = !set.contains(pos.north()) || !set.contains(pos.south());
            int edges = (xEdge ? 1 : 0) + (yEdge ? 1 : 0) + (zEdge ? 1 : 0);
            if (edges >= 1) {
                boundary.add(pos);
            }
        }
        return boundary;
    }
}
