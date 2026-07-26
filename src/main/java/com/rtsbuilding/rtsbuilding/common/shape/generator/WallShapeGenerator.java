package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 墙体（垂直拉伸线）形状生成器。
 * <p>
 * 先生成基线（在 XZ 平面上从起点到终点的直线），
 * 然后将基线沿 Y 轴按高度偏移量垂直拉伸，形成一面墙。
 * 支持 FILL（实心墙）和 HOLLOW（边框）两种模式。
 */
public class WallShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "wall";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 计算 XZ 平面和 Y 轴上的偏移量并限制范围
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());
        int dy = clampOffset(input.heightOffset());

        // 生成基线（起点 → 终点在 XZ 平面上的投影）
        BlockPos endPos = new BlockPos(input.start().getX() + dx, input.start().getY(), input.start().getZ() + dz);
        BlockPos baseStart = input.start();
        List<BlockPos> base = generateLinePositions(baseStart, endPos);

        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);
        List<BlockPos> result = new ArrayList<>();

        // 从上往下逐层生成墙面位置
        for (int iy = maxY; iy >= minY; iy--) {
            for (int i = 0; i < base.size(); i++) {
                BlockPos basePos = base.get(i);
                boolean endColumn = (base.size() <= 1) || (i == 0 || i == base.size() - 1);
                // 空心模式下只保留端部列和顶部/底部行
                if (fillMode != ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) {
                    continue;
                }
                result.add(basePos.above(iy));
            }
        }

        return result;
    }
}
