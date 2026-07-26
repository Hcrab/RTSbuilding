package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 圆形形状生成器（在 XZ 平面上的单层圆）。
 * <p>
 * 半径由起点到终点在 XZ 平面上的投影距离决定。
 * 支持 FILL（实心圆）和 HOLLOW（空心圆环）两种模式。
 * FILL 模式会使用洪水填充（Flood-Fill）填补内部空洞。
 */
public class CircleShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "circle";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 计算 XZ 平面上的偏移量
        int dx = input.end().getX() - input.start().getX();
        int dz = input.end().getZ() - input.start().getZ();

        // 计算半径并限制最大值
        double radius = Math.sqrt((dx * (double) dx) + (dz * (double) dz));
        int r = Math.max(0, (int) Math.round(radius));
        r = Math.min(r, 64);

        int outer2 = r * r;
        int inner = Math.max(0, r - 1);
        int inner2 = inner * inner;

        // 遍历包围盒内的所有 XZ 坐标，筛选出圆形范围内的位置
        List<BlockPos> result = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int dist2 = x * x + z * z;
                boolean inOuter = dist2 <= outer2;
                boolean inInner = dist2 < inner2;
                // 空心模式跳过内部点
                if (!inOuter || (fillMode != ShapeFillMode.FILL && inInner)) {
                    continue;
                }
                result.add(input.start().offset(x, 0, z));
            }
        }

        // 实心模式需要填补栅格化产生的内部空洞
        if (fillMode == ShapeFillMode.FILL) {
            result = fillInternalHoles(result);
        }

        return result;
    }

    /**
     * 使用洪水填充方法填补内部空洞。
     * <p>
     * 在投影的 2D 网格上执行，处理圆形栅格化过程中产生的间隙。
     * 原理：从边界外开始标记所有"外部"区域，
     * 剩下的未标记位置即为需要填充的内部空洞。
     *
     * @param positions 当前生成的位置列表
     * @return 填补空洞后的完整位置列表
     */
    private static List<BlockPos> fillInternalHoles(List<BlockPos> positions) {
        if (positions.isEmpty()) return positions;

        // 确定包围盒边界
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 确定一个代表性的 Y 层
        int yLevel = positions.get(0).getY();

        java.util.Set<BlockPos> filled = new java.util.HashSet<>(positions);
        java.util.Set<BlockPos> outside = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

        // 将包围盒边界外的所有格子标记为"外部"种子
        for (int x = minX - 1; x <= maxX + 1; x++) {
            tryEnqueue(new BlockPos(x, yLevel, minZ - 1), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(new BlockPos(x, yLevel, maxZ + 1), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }
        for (int z = minZ; z <= maxZ; z++) {
            tryEnqueue(new BlockPos(minX - 1, yLevel, z), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(new BlockPos(maxX + 1, yLevel, z), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }

        // 洪水填充：从边界向外扩散，标记所有可达的"外部"区域
        while (!queue.isEmpty()) {
            BlockPos cur = queue.removeFirst();
            tryEnqueue(cur.east(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.west(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.north(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.south(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }

        // 收集既不在 filled 也不在 outside 中的位置（即内部空洞）
        List<BlockPos> dense = new ArrayList<>(positions);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, yLevel, z);
                if (!filled.contains(pos) && !outside.contains(pos)) {
                    dense.add(pos);
                }
            }
        }
        return dense;
    }

    /**
     * 尝试将位置加入外部区域队列。
     * <p>
     * 如果该位置在边界范围内、且不被 filled 或 outside 包含，则加入队列。
     */
    private static void tryEnqueue(BlockPos pos, java.util.Set<BlockPos> filled,
                                    java.util.Set<BlockPos> outside, java.util.ArrayDeque<BlockPos> queue,
                                    int minX, int maxX, int minZ, int maxZ, int yLevel) {
        if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) return;
        if (pos.getY() != yLevel) return;
        if (filled.contains(pos) || outside.contains(pos)) return;
        outside.add(pos);
        queue.addLast(pos);
    }
}