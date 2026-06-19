package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 长方体（BOX / 3D 立方体）形状生成器。
 * <p>
 * 生成由两个对角点和可选高度偏移定义的长方体内的所有方块位置。
 * 支持 FILL（实心）、HOLLOW（空心）和 SKELETON（骨架）三种填充模式。
 */
public class BoxShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "box";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 计算三个轴上的偏移量并限制最大范围
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());
        int dy = clampOffset(input.heightOffset());

        // 确定各轴的最小/最大范围
        int minX = Math.min(0, dx);
        int maxX = Math.max(0, dx);
        int minZ = Math.min(0, dz);
        int maxZ = Math.max(0, dz);
        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);

        // 生成实心长方体的所有方块位置（从上往下逐层）
        List<BlockPos> full = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    full.add(input.start().offset(x, y, z));
                }
            }
        }

        // 实心模式下直接返回全部位置，空心/骨架模式则过滤出边界
        if (fillMode == ShapeFillMode.FILL || full.isEmpty()) {
            return full;
        }

        return filterBoundary(full, minY, maxY);
    }
}